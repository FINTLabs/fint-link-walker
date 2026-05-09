// Package summary builds report.Summary aggregates — including the
// nested per-component and per-resource breakdowns the reader exposes
// as both the /summary endpoint and as Prometheus metrics — from a
// populated Index plus the rows the validator produced.
package summary

import (
	"sort"

	"github.com/FINTLabs/fint-link-walker/go-services/pkg/report"
	"github.com/FINTLabs/fint-link-walker/go-services/scanner/internal/index"
)

// Build computes totals and the nested per-component → per-resource
// histogram. components is the configured-for-this-scan list, attached
// as-is at the top of the report.
func Build(idx *index.Index, rows []report.Row, components []string) report.Summary {
	// Walk the index to accumulate per-(component, resource) record + ref
	// counts.
	type compResKey struct{ comp, res string }
	type bucket struct {
		records, refs int
	}
	byCompRes := map[compResKey]*bucket{}
	byComp := map[string]*bucket{}
	totalRecords, totalRefs := 0, 0

	for _, r := range idx.Records {
		k := compResKey{r.Component, r.ResourceName}
		b := byCompRes[k]
		if b == nil {
			b = &bucket{}
			byCompRes[k] = b
		}
		b.records++
		b.refs += len(r.Outbound)

		c := byComp[r.Component]
		if c == nil {
			c = &bucket{}
			byComp[r.Component] = c
		}
		c.records++
		c.refs += len(r.Outbound)

		totalRecords++
		totalRefs += len(r.Outbound)
	}

	// Walk the rows to accumulate per-(component, resource) broken counts
	// keyed by problem type.
	rowsByCompRes := map[compResKey]map[string]int{}
	rowsByComp := map[string]map[string]int{}
	rowsByProblem := map[string]int{}
	totalBroken := 0

	for _, row := range rows {
		k := compResKey{row.Component, row.Resource}
		m := rowsByCompRes[k]
		if m == nil {
			m = map[string]int{}
			rowsByCompRes[k] = m
		}
		m[row.ProblemType]++

		cm := rowsByComp[row.Component]
		if cm == nil {
			cm = map[string]int{}
			rowsByComp[row.Component] = cm
		}
		cm[row.ProblemType]++

		rowsByProblem[row.ProblemType]++
		totalBroken++
	}

	// Build per-component summaries with their nested per-resource lists.
	compNames := make([]string, 0, len(byComp))
	for c := range byComp {
		compNames = append(compNames, c)
	}
	sort.Strings(compNames)

	compSummaries := make([]report.ComponentSummary, 0, len(compNames))
	for _, comp := range compNames {
		cb := byComp[comp]
		compBroken := sumValues(rowsByComp[comp])

		// Resources within this component.
		var resNames []string
		for k := range byCompRes {
			if k.comp == comp {
				resNames = append(resNames, k.res)
			}
		}
		sort.Strings(resNames)

		resSummaries := make([]report.ResourceSummary, 0, len(resNames))
		for _, res := range resNames {
			b := byCompRes[compResKey{comp, res}]
			byProblem := rowsByCompRes[compResKey{comp, res}]
			broken := sumValues(byProblem)
			resSummaries = append(resSummaries, report.ResourceSummary{
				Resource:         res,
				TotalRecords:     b.records,
				TotalRefs:        b.refs,
				BrokenLinkCount:  broken,
				IntegrityPercent: integrity(b.refs, broken),
				ByProblemType:    nilIfEmpty(byProblem),
			})
		}

		compSummaries = append(compSummaries, report.ComponentSummary{
			Component:        comp,
			TotalRecords:     cb.records,
			TotalRefs:        cb.refs,
			BrokenLinkCount:  compBroken,
			IntegrityPercent: integrity(cb.refs, compBroken),
			ByProblemType:    nilIfEmpty(rowsByComp[comp]),
			Resources:        resSummaries,
		})
	}

	return report.Summary{
		TotalRecords:     totalRecords,
		TotalRefs:        totalRefs,
		BrokenLinkCount:  totalBroken,
		IntegrityPercent: integrity(totalRefs, totalBroken),
		ByProblemType:    nilIfEmpty(rowsByProblem),
		Components:       compSummaries,
	}
}

// integrity returns 100 × (1 − broken/refs). When refs is 0 (failed or
// empty scan) returns 100 — there's nothing to measure as broken.
func integrity(refs, broken int) float64 {
	if refs == 0 {
		return 100.0
	}
	return 100.0 * float64(refs-broken) / float64(refs)
}

func sumValues(m map[string]int) int {
	total := 0
	for _, v := range m {
		total += v
	}
	return total
}

func nilIfEmpty(m map[string]int) map[string]int {
	if len(m) == 0 {
		return nil
	}
	return m
}
