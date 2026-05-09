// Package report holds the data model shared between scanner (producer)
// and reader (consumer) of link-integrity scan results. Mirrors the
// Kotlin core/report types — same field names where reasonable so the
// JSON wire format stays compatible across implementations.
package report

import "time"

// Problem-type constants. Keep in sync with the Kotlin scanner so
// dashboards work against either implementation.
const (
	ProblemMissingResource              = "missing-resource"
	ProblemUnknownLink                  = "unknown-link"
	ProblemMissingBackLinkAdapter       = "missing-back-link-adapter"
	ProblemMissingBackLinkAutorelation  = "missing-back-link-autorelation"
)

// Row is a single broken-link finding.
type Row struct {
	OrgID               string `json:"orgId"`
	Component           string `json:"component"`
	Resource            string `json:"resource"`
	ProblemType         string `json:"problemType"`
	SourceSelf          string `json:"sourceSelf"`
	TargetHref          string `json:"targetHref"`
	RelationName        string `json:"relationName,omitempty"`
	ExpectedInverseName string `json:"expectedInverseName,omitempty"`
}

// Summary is the aggregate view of one scan.
type Summary struct {
	TotalRecords     int            `json:"totalRecords"`
	TotalRefs        int            `json:"totalRefs"`
	BrokenLinkCount  int            `json:"brokenLinkCount"`
	IntegrityPercent float64        `json:"integrityPercent"`
	ByProblemType    map[string]int `json:"byProblemType"`
	Components       []string       `json:"components"`
}

// Report is the full produced artifact of one scan: summary + every row.
// Persisted in full by the scanner; the reader serves slices of it.
type Report struct {
	OrgID           string    `json:"orgId"`
	ScanCompletedAt time.Time `json:"scanCompletedAt"`
	Components      []string  `json:"components"`
	Summary         Summary   `json:"summary"`
	Rows            []Row     `json:"rows"`
}

// LatestSummary is what the reader returns from /report/{orgId}/summary —
// everything except the row list (which is paginated separately).
type LatestSummary struct {
	OrgID           string    `json:"orgId"`
	ScanCompletedAt time.Time `json:"scanCompletedAt"`
	Components      []string  `json:"components"`
	Summary         Summary   `json:"summary"`
}

// RowFilter narrows a /report/{orgId}/rows query. Empty fields = no filter
// on that field. Filters AND-combine.
type RowFilter struct {
	Component   string
	Resource    string
	ProblemType string
}

// PagedRows is the paginated row response. TotalRows is the post-filter
// count — i.e. the count consistent with what's being paged.
type PagedRows struct {
	TotalRows int   `json:"totalRows"`
	Page      int   `json:"page"`
	Size      int   `json:"size"`
	Rows      []Row `json:"rows"`
}

const MaxPageSize = 1000
