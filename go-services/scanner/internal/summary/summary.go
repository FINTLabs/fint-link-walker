// Package summary builds report.Summary aggregates from a populated
// Index plus the rows the validator produced.
package summary

import (
	"github.com/FINTLabs/fint-link-walker/go-services/pkg/report"
	"github.com/FINTLabs/fint-link-walker/go-services/scanner/internal/index"
)

// Build computes totals and the per-problem-type histogram. components
// is the list configured for this scan, attached as-is for the report.
func Build(idx *index.Index, rows []report.Row, components []string) report.Summary {
	totalRefs := 0
	for _, r := range idx.Records {
		totalRefs += len(r.Outbound)
	}

	byProblem := make(map[string]int, len(rows))
	for _, row := range rows {
		byProblem[row.ProblemType]++
	}

	integrity := 100.0
	if totalRefs > 0 {
		integrity = 100.0 * float64(totalRefs-len(rows)) / float64(totalRefs)
	}

	return report.Summary{
		TotalRecords:     len(idx.Records),
		TotalRefs:        totalRefs,
		BrokenLinkCount:  len(rows),
		IntegrityPercent: integrity,
		ByProblemType:    byProblem,
		Components:       components,
	}
}
