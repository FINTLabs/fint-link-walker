// Package index parses HAL collection bodies into MinimalRecords and
// keeps a tenant-scoped store keyed by canonical href so the validator
// can answer "does this link target exist?" in O(1).
package index

import (
	"encoding/json"
	"io"
	"regexp"
	"strings"
	"sync"
)

// Record is the minimum slice of a FINT resource the link walker needs:
// the identifying canonical hrefs (self), the outbound relation hrefs,
// and the originating component/type so reports can be attributed.
type Record struct {
	Component     string
	ResourceName  string
	TypeKey       string // "component:Type" — for runtime.New lookups
	CanonicalKeys []string
	Outbound      []OutboundRef
}

type OutboundRef struct {
	RelationName string
	Target       string // canonical
}

// DisplaySelf returns the first canonical key, or a placeholder when
// the record has no self link. Useful for human-readable reports.
func (r Record) DisplaySelf() string {
	if len(r.CanonicalKeys) > 0 {
		return r.CanonicalKeys[0]
	}
	return "<no-self-link>"
}

// HasOutboundTo reports whether this record has an outbound ref with the
// given relation name pointing at one of the supplied targets.
func (r Record) HasOutboundTo(relationName string, targets map[string]struct{}) bool {
	for _, ref := range r.Outbound {
		if !strings.EqualFold(ref.RelationName, relationName) {
			continue
		}
		if _, ok := targets[ref.Target]; ok {
			return true
		}
	}
	return false
}

// Index holds every record parsed during a scan plus a canonical-key
// reverse lookup so outbound refs can be resolved without scanning.
//
// Add and AddAll are safe to call concurrently. Reads (Resolves,
// RecordAt) are NOT mutex-protected — they assume the fetch phase is
// done and no further writes will occur.
type Index struct {
	mu      sync.Mutex
	Records []Record
	byKey   map[string]*Record
}

func New() *Index {
	return &Index{byKey: make(map[string]*Record)}
}

func (i *Index) Add(rec Record) {
	i.mu.Lock()
	defer i.mu.Unlock()
	i.addLocked(rec)
}

// AddAll inserts a batch of records under one mutex acquisition. Used
// by the parallel fetch path to reduce per-record lock contention.
func (i *Index) AddAll(recs []Record) {
	if len(recs) == 0 {
		return
	}
	i.mu.Lock()
	defer i.mu.Unlock()
	for _, rec := range recs {
		i.addLocked(rec)
	}
}

func (i *Index) addLocked(rec Record) {
	i.Records = append(i.Records, rec)
	added := &i.Records[len(i.Records)-1]
	for _, k := range added.CanonicalKeys {
		i.byKey[k] = added
	}
}

func (i *Index) Resolves(canonical string) bool {
	_, ok := i.byKey[canonical]
	return ok
}

func (i *Index) RecordAt(canonical string) (*Record, bool) {
	r, ok := i.byKey[canonical]
	return r, ok
}

// hrefRegex captures the (prefix, idField, idValue) of a FINT resource href.
// Prefix is host + at least three path segments so sub-namespaced types
// like felles/kodeverk/iso/kjonn match too.
var hrefRegex = regexp.MustCompile(`^(https?://[^/]+/(?:[^/]+/){2,}[^/]+)/([^/]+)/([^/?#]+)$`)

// Canonicalize lowercases the prefix + idField, preserves the idValue
// case, trims trailing slashes. URLs that don't match the expected shape
// are returned lowercased as a fallback.
func Canonicalize(href string) string {
	h := strings.TrimSpace(strings.TrimRight(href, "/"))
	m := hrefRegex.FindStringSubmatch(h)
	if m == nil {
		return strings.ToLower(h)
	}
	return strings.ToLower(m[1]) + "/" + strings.ToLower(m[2]) + "/" + m[3]
}

// halBody is the minimum HAL shape we read.
type halBody struct {
	Embedded struct {
		Entries []json.RawMessage `json:"_entries"`
	} `json:"_embedded"`
}

type halEntry struct {
	Links map[string][]halLink `json:"_links"`
}

type halLink struct {
	Href string `json:"href"`
}

// ExtractRecords reads a HAL collection body and produces one Record per
// entry. The component, resourceName, and typeKey are attached as
// metadata so downstream code can attribute findings.
func ExtractRecords(body io.Reader, component, resourceName, typeKey string) ([]Record, error) {
	var hal halBody
	if err := json.NewDecoder(body).Decode(&hal); err != nil {
		return nil, err
	}
	out := make([]Record, 0, len(hal.Embedded.Entries))
	for _, raw := range hal.Embedded.Entries {
		out = append(out, extractOne(raw, component, resourceName, typeKey))
	}
	return out, nil
}

func extractOne(raw json.RawMessage, component, resourceName, typeKey string) Record {
	var entry halEntry
	_ = json.Unmarshal(raw, &entry) // missing _links is fine — empty record

	var canonical []string
	var outbound []OutboundRef
	for relName, links := range entry.Links {
		isSelf := strings.EqualFold(relName, "self")
		var interned string
		if !isSelf {
			interned = internRelation(relName)
		}
		for _, l := range links {
			if l.Href == "" {
				continue
			}
			if isSelf {
				canonical = append(canonical, Canonicalize(l.Href))
			} else {
				outbound = append(outbound, OutboundRef{RelationName: interned, Target: Canonicalize(l.Href)})
			}
		}
	}
	return Record{
		Component:     component,
		ResourceName:  resourceName,
		TypeKey:       typeKey,
		CanonicalKeys: canonical,
		Outbound:      outbound,
	}
}
