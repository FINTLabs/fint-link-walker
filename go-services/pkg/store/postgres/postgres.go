// Package postgres implements store.Store on top of pgx + Postgres.
// Schema mirrors the JPA entities so reports written here are servable
// by the Kotlin reader and vice versa.
package postgres

import (
	"context"
	_ "embed"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/FINTLabs/fint-link-walker/go-services/pkg/report"
	"github.com/FINTLabs/fint-link-walker/go-services/pkg/store"
)

//go:embed schema.sql
var schemaSQL string

// Store is a Postgres-backed report store. The connection pool is owned
// by the Store; call Close when shutting down.
type Store struct {
	pool *pgxpool.Pool
}

// New connects to Postgres at dsn, applies the (idempotent) schema, and
// returns a ready-to-use Store. dsn examples:
//
//	postgres://linkwalker:linkwalker@localhost:5432/linkwalker?sslmode=disable
//	postgresql://user:pw@host:5432/db
func New(ctx context.Context, dsn string) (*Store, error) {
	pool, err := pgxpool.New(ctx, dsn)
	if err != nil {
		return nil, fmt.Errorf("postgres: connect: %w", err)
	}
	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("postgres: ping: %w", err)
	}
	if _, err := pool.Exec(ctx, schemaSQL); err != nil {
		pool.Close()
		return nil, fmt.Errorf("postgres: migrate: %w", err)
	}
	return &Store{pool: pool}, nil
}

func (s *Store) Close() { s.pool.Close() }

// Publish writes one summary row + COPY-loads every row in r.Rows under
// a fresh scan_id, atomically. Both inserts share a transaction so a
// half-published scan is impossible.
func (s *Store) Publish(r report.Report) error {
	ctx := context.Background()
	tx, err := s.pool.Begin(ctx)
	if err != nil {
		return fmt.Errorf("postgres: begin: %w", err)
	}
	defer func() { _ = tx.Rollback(ctx) }()

	scanID := uuid.New()

	summaryDoc := report.LatestSummary{
		OrgID:           r.OrgID,
		ScanCompletedAt: r.ScanCompletedAt,
		Components:      r.Components,
		Summary:         r.Summary,
	}
	summaryJSON, err := json.Marshal(summaryDoc)
	if err != nil {
		return fmt.Errorf("postgres: marshal summary: %w", err)
	}

	_, err = tx.Exec(ctx, `
		INSERT INTO report_summary (id, scan_id, org_id, scan_completed_at, summary_json)
		VALUES ($1, $2, $3, $4, $5)
	`, uuid.New(), scanID, r.OrgID, r.ScanCompletedAt, string(summaryJSON))
	if err != nil {
		return fmt.Errorf("postgres: insert summary: %w", err)
	}

	if len(r.Rows) > 0 {
		copyRows := make([][]any, len(r.Rows))
		for i, row := range r.Rows {
			copyRows[i] = []any{
				scanID,
				row.OrgID,
				r.ScanCompletedAt,
				row.Component,
				row.Resource,
				row.ProblemType,
				row.SourceSelf,
				row.TargetHref,
				nullIfEmpty(row.RelationName),
				nullIfEmpty(row.ExpectedInverseName),
			}
		}
		_, err = tx.CopyFrom(ctx,
			pgx.Identifier{"report_row"},
			[]string{"scan_id", "org_id", "scan_completed_at", "component", "resource",
				"problem_type", "source_self", "target_href", "relation_name", "expected_inverse_name"},
			pgx.CopyFromRows(copyRows),
		)
		if err != nil {
			return fmt.Errorf("postgres: copy rows: %w", err)
		}
	}

	if err := tx.Commit(ctx); err != nil {
		return fmt.Errorf("postgres: commit: %w", err)
	}
	return nil
}

// GetSummary returns the latest summary for orgID by scan_completed_at.
func (s *Store) GetSummary(orgID string) (*report.LatestSummary, error) {
	var summaryJSON string
	err := s.pool.QueryRow(context.Background(), `
		SELECT summary_json FROM report_summary
		WHERE org_id = $1
		ORDER BY scan_completed_at DESC
		LIMIT 1
	`, orgID).Scan(&summaryJSON)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, store.ErrNotFound{OrgID: orgID}
	}
	if err != nil {
		return nil, fmt.Errorf("postgres: get summary: %w", err)
	}
	var ls report.LatestSummary
	if err := json.Unmarshal([]byte(summaryJSON), &ls); err != nil {
		return nil, fmt.Errorf("postgres: decode summary: %w", err)
	}
	return &ls, nil
}

// ListSummaries returns the latest summary per orgID across the table.
func (s *Store) ListSummaries() ([]report.LatestSummary, error) {
	rows, err := s.pool.Query(context.Background(), `
		SELECT DISTINCT ON (org_id) summary_json
		FROM report_summary
		ORDER BY org_id, scan_completed_at DESC
	`)
	if err != nil {
		return nil, fmt.Errorf("postgres: list summaries: %w", err)
	}
	defer rows.Close()

	var out []report.LatestSummary
	for rows.Next() {
		var summaryJSON string
		if err := rows.Scan(&summaryJSON); err != nil {
			return nil, fmt.Errorf("postgres: scan: %w", err)
		}
		var ls report.LatestSummary
		if err := json.Unmarshal([]byte(summaryJSON), &ls); err != nil {
			continue // skip malformed; not fatal for listing
		}
		out = append(out, ls)
	}
	return out, rows.Err()
}

// FindRows returns a page of rows from the latest scan for orgID, with
// optional component/resource/problemType filters AND-combined.
func (s *Store) FindRows(orgID string, filter report.RowFilter, page, size int) (*report.PagedRows, error) {
	if size <= 0 {
		size = 100
	}
	if size > report.MaxPageSize {
		size = report.MaxPageSize
	}
	if page < 0 {
		page = 0
	}

	ctx := context.Background()

	var scanID uuid.UUID
	var scanCompletedAt time.Time
	err := s.pool.QueryRow(ctx, `
		SELECT scan_id, scan_completed_at FROM report_summary
		WHERE org_id = $1
		ORDER BY scan_completed_at DESC
		LIMIT 1
	`, orgID).Scan(&scanID, &scanCompletedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, store.ErrNotFound{OrgID: orgID}
	}
	if err != nil {
		return nil, fmt.Errorf("postgres: latest scan: %w", err)
	}

	component := nullIfEmpty(filter.Component)
	resource := nullIfEmpty(filter.Resource)
	problemType := nullIfEmpty(filter.ProblemType)

	var totalRows int
	err = s.pool.QueryRow(ctx, `
		SELECT COUNT(*) FROM report_row
		WHERE scan_id = $1
		  AND ($2::text IS NULL OR component = $2)
		  AND ($3::text IS NULL OR resource = $3)
		  AND ($4::text IS NULL OR problem_type = $4)
	`, scanID, component, resource, problemType).Scan(&totalRows)
	if err != nil {
		return nil, fmt.Errorf("postgres: count rows: %w", err)
	}

	rows, err := s.pool.Query(ctx, `
		SELECT org_id, component, resource, problem_type, source_self, target_href,
		       COALESCE(relation_name, ''), COALESCE(expected_inverse_name, '')
		FROM report_row
		WHERE scan_id = $1
		  AND ($2::text IS NULL OR component = $2)
		  AND ($3::text IS NULL OR resource = $3)
		  AND ($4::text IS NULL OR problem_type = $4)
		ORDER BY id
		LIMIT $5 OFFSET $6
	`, scanID, component, resource, problemType, size, page*size)
	if err != nil {
		return nil, fmt.Errorf("postgres: page rows: %w", err)
	}
	defer rows.Close()

	out := make([]report.Row, 0, size)
	for rows.Next() {
		var r report.Row
		if err := rows.Scan(
			&r.OrgID, &r.Component, &r.Resource, &r.ProblemType,
			&r.SourceSelf, &r.TargetHref, &r.RelationName, &r.ExpectedInverseName,
		); err != nil {
			return nil, fmt.Errorf("postgres: scan row: %w", err)
		}
		out = append(out, r)
	}
	return &report.PagedRows{
		TotalRows: totalRows,
		Page:      page,
		Size:      size,
		Rows:      out,
	}, rows.Err()
}

func nullIfEmpty(s string) any {
	if s == "" {
		return nil
	}
	return s
}
