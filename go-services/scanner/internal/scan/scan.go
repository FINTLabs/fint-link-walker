// Package scan orchestrates a full link-walking scan.
//
// Two-phase parallel fetch:
//
//  1. Discover sizes: GET /{type-path}/cache/size for every hovedklasse
//     type concurrently. Yields totalItems per type.
//  2. Fetch pages: for every (type, offset) tuple in [0, totalItems),
//     GET /{type-path}?size=PAGE&offset=N concurrently. Each response
//     is parsed and merged into a single Index.
//
// FetchConcurrency caps in-flight HTTP requests across both phases.
// Validation and publish run sequentially after the index is built.
package scan

import (
	"context"
	"fmt"
	"log/slog"
	"os"
	"runtime"
	"runtime/pprof"
	"slices"
	"strings"
	"sync/atomic"
	"time"

	"github.com/FINTLabs/fint-model-go/manifest"
	"golang.org/x/sync/errgroup"

	"github.com/FINTLabs/fint-link-walker/go-services/pkg/report"
	"github.com/FINTLabs/fint-link-walker/go-services/pkg/store"
	"github.com/FINTLabs/fint-link-walker/go-services/scanner/internal/fintapi"
	"github.com/FINTLabs/fint-link-walker/go-services/scanner/internal/index"
	"github.com/FINTLabs/fint-link-walker/go-services/scanner/internal/summary"
	"github.com/FINTLabs/fint-link-walker/go-services/scanner/internal/validate"
)

type Scanner struct {
	OrgID           string
	Components      []string
	PageSize        int
	API             *fintapi.Client
	Store           store.Store
	Logger          *slog.Logger
	HeapProfilePath string // optional; if set, writes per-phase heap profiles
}

// Run performs one scan and publishes the resulting report.
func (s *Scanner) Run(ctx context.Context) (*report.Report, error) {
	totalStart := time.Now()
	var peakSysMB uint64
	mem := func() (heapMB uint64, sysMB uint64) {
		var ms runtime.MemStats
		runtime.ReadMemStats(&ms)
		sysMB = ms.Sys >> 20
		if sysMB > peakSysMB {
			peakSysMB = sysMB
		}
		return ms.HeapAlloc >> 20, sysMB
	}

	idx := index.New()

	// Build the flat list of (component, type) tuples to scan.
	type typeRef struct {
		comp string
		t    manifest.TypeInfo
	}
	var allTypes []typeRef
	stats := make(map[string]*compStat)
	for _, c := range manifest.Components {
		if !slices.Contains(s.Components, c.Name) {
			continue
		}
		stats[c.Name] = &compStat{}
		for _, t := range c.Types {
			if t.Stereotype != "hovedklasse" {
				continue
			}
			allTypes = append(allTypes, typeRef{c.Name, t})
		}
	}

	// --- Phase 1: discover sizes in parallel ---
	type sizeResult struct {
		comp     string
		typeName string
		typeKey  string
		path     string
		size     int
		ok       bool
	}
	sizes := make([]sizeResult, len(allTypes))

	discoverStart := time.Now()
	g1, gctx1 := errgroup.WithContext(ctx)
	for i, tref := range allTypes {
		i, tref := i, tref
		g1.Go(func() error {
			start := time.Now()
			n, err := s.API.CacheSize(gctx1, tref.t.Path)
			stats[tref.comp].sizeMs.Add(time.Since(start).Milliseconds())
			if err != nil {
				stats[tref.comp].typesFailed.Add(1)
				s.Logger.Warn("size discovery failed",
					"component", tref.comp, "type", tref.t.Name, "err", err)
				return nil
			}
			sizes[i] = sizeResult{tref.comp, tref.t.Name, tref.t.Key, tref.t.Path, n, true}
			s.Logger.Debug("size discovered",
				"type", tref.t.Key, "size", n, "elapsedMs", time.Since(start).Milliseconds())
			return nil
		})
	}
	_ = g1.Wait() // closures swallow errors and log warns
	discoverElapsed := time.Since(discoverStart)

	// Build the page task list. Empty types (size==0) count as ok with 0 records.
	type pageTask struct {
		comp     string
		typeName string
		typeKey  string
		path     string
		offset   int
	}
	var tasks []pageTask
	for _, sr := range sizes {
		if !sr.ok {
			continue
		}
		if sr.size == 0 {
			stats[sr.comp].typesOk.Add(1)
			continue
		}
		stats[sr.comp].typesOk.Add(1)
		stats[sr.comp].records.Add(0) // tracks running total via page workers
		for off := 0; off < sr.size; off += s.PageSize {
			tasks = append(tasks, pageTask{sr.comp, sr.typeName, sr.typeKey, sr.path, off})
		}
	}
	s.Logger.Info("discovery done",
		"types", len(allTypes),
		"pages", len(tasks),
		"elapsedMs", discoverElapsed.Milliseconds(),
	)

	// --- Phase 2: fetch pages in parallel ---
	fetchStart := time.Now()
	g2, gctx2 := errgroup.WithContext(ctx)
	for _, t := range tasks {
		t := t
		g2.Go(func() error {
			start := time.Now()
			body, err := s.API.Page(gctx2, t.path, s.PageSize, t.offset)
			if err != nil {
				stats[t.comp].pagesFailed.Add(1)
				s.Logger.Warn("page fetch failed",
					"component", t.comp, "type", t.typeName, "offset", t.offset, "err", err)
				return nil
			}
			records, err := index.ExtractRecords(body, t.comp, t.typeName, t.typeKey)
			body.Close()
			elapsed := time.Since(start).Milliseconds()
			stats[t.comp].pageMs.Add(elapsed)
			if err != nil {
				stats[t.comp].pagesFailed.Add(1)
				s.Logger.Warn("page extract failed",
					"component", t.comp, "type", t.typeName, "offset", t.offset, "err", err)
				return nil
			}
			stats[t.comp].pagesOk.Add(1)
			stats[t.comp].records.Add(int64(len(records)))
			idx.AddAll(records)
			s.Logger.Debug("page fetched",
				"type", t.typeKey, "offset", t.offset,
				"records", len(records), "elapsedMs", elapsed)
			return nil
		})
	}
	_ = g2.Wait()
	fetchElapsed := time.Since(fetchStart)

	// Per-component summary.
	for _, c := range manifest.Components {
		if !slices.Contains(s.Components, c.Name) {
			continue
		}
		cs := stats[c.Name]
		s.Logger.Info("component done",
			"component", c.Name,
			"typesOk", cs.typesOk.Load(),
			"typesFailed", cs.typesFailed.Load(),
			"pagesOk", cs.pagesOk.Load(),
			"pagesFailed", cs.pagesFailed.Load(),
			"records", cs.records.Load(),
			"sizeMs", cs.sizeMs.Load(),
			"pageMs", cs.pageMs.Load(),
		)
	}
	heap, sys := mem()
	s.Logger.Info("index built",
		"records", len(idx.Records),
		"elapsedMs", fetchElapsed.Milliseconds(),
		"heapMB", heap, "sysMB", sys,
	)
	s.dumpHeap("after-fetch")

	// --- validate phase ---
	validateStart := time.Now()
	rows := validate.New(s.OrgID).Run(idx)
	validateElapsed := time.Since(validateStart)
	heap, sys = mem()
	s.Logger.Info("validation complete",
		"rows", len(rows),
		"elapsedMs", validateElapsed.Milliseconds(),
		"heapMB", heap, "sysMB", sys,
	)
	s.dumpHeap("after-validate")

	// --- publish phase ---
	r := report.Report{
		OrgID:           s.OrgID,
		ScanCompletedAt: time.Now().UTC(),
		Components:      s.Components,
		Summary:         summary.Build(idx, rows, s.Components),
		Rows:            rows,
	}
	publishStart := time.Now()
	if err := s.Store.Publish(r); err != nil {
		return nil, fmt.Errorf("publish: %w", err)
	}
	publishElapsed := time.Since(publishStart)
	heap, sys = mem()
	s.Logger.Info("report published",
		"orgId", r.OrgID,
		"records", r.Summary.TotalRecords,
		"refs", r.Summary.TotalRefs,
		"broken", r.Summary.BrokenLinkCount,
		"integrity", r.Summary.IntegrityPercent,
		"elapsedMs", publishElapsed.Milliseconds(),
		"heapMB", heap, "sysMB", sys,
	)
	s.dumpHeap("after-publish")

	s.Logger.Info("scan total",
		"discoverMs", discoverElapsed.Milliseconds(),
		"fetchMs", fetchElapsed.Milliseconds(),
		"validateMs", validateElapsed.Milliseconds(),
		"publishMs", publishElapsed.Milliseconds(),
		"totalMs", time.Since(totalStart).Milliseconds(),
		"peakSysMB", peakSysMB,
	)
	return &r, nil
}

type compStat struct {
	typesOk     atomic.Int64
	typesFailed atomic.Int64
	pagesOk     atomic.Int64
	pagesFailed atomic.Int64
	records     atomic.Int64
	sizeMs      atomic.Int64
	pageMs      atomic.Int64
}

// dumpHeap writes a heap profile to a phase-suffixed file, e.g.
// HEAP_PROFILE=/tmp/heap.prof + phase="after-fetch" → /tmp/heap-after-fetch.prof.
// Forces a GC first so the profile reflects truly-live data, not garbage
// the collector hasn't gotten to. No-op when HeapProfilePath is empty.
func (s *Scanner) dumpHeap(phase string) {
	if s.HeapProfilePath == "" {
		return
	}
	base := strings.TrimSuffix(s.HeapProfilePath, ".prof")
	path := base + "-" + phase + ".prof"
	f, err := os.Create(path)
	if err != nil {
		s.Logger.Warn("heap profile create", "path", path, "err", err)
		return
	}
	defer f.Close()
	runtime.GC()
	if err := pprof.WriteHeapProfile(f); err != nil {
		s.Logger.Warn("heap profile write", "path", path, "err", err)
		return
	}
	s.Logger.Info("heap profile written", "path", path, "phase", phase)
}
