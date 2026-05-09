// Package store defines the persistence interface for scan reports.
// Scanner publishes; reader consumes. The interface mirrors the Kotlin
// ReportStore so the Postgres schema is shared between implementations.
package store

import (
	"github.com/FINTLabs/fint-link-walker/go-services/pkg/report"
)

type Store interface {
	Publish(r report.Report) error
	GetSummary(orgID string) (*report.LatestSummary, error)
	ListSummaries() ([]report.LatestSummary, error)
	FindRows(orgID string, filter report.RowFilter, page, size int) (*report.PagedRows, error)
}

// ErrNotFound is returned by GetSummary / FindRows when no report exists
// for the given orgID. Callers map this to HTTP 404.
type ErrNotFound struct{ OrgID string }

func (e ErrNotFound) Error() string { return "report not found for org: " + e.OrgID }
