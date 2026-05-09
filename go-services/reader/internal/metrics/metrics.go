// Package metrics exposes link-walker scan results as Prometheus metrics.
//
// Five gauges, matching the Kotlin reader's SummaryMetrics 1:1 so
// existing Grafana dashboards keep working without query changes:
//
//	link_walker_org_integrity_percent {orgId}
//	link_walker_integrity_percent     {orgId, component, resource}
//	link_walker_records_count         {orgId, component, resource}
//	link_walker_refs_count            {orgId, component, resource}
//	link_walker_broken_links          {orgId, component, resource, problem_type}
//
// Implemented as a Prometheus Collector that queries the Store on every
// /metrics scrape — fresh data with no background goroutine, no stale
// cache. Postgres roundtrip per scrape is fine at typical scrape
// intervals (every 15-60s).
package metrics

import (
	"log/slog"

	"github.com/prometheus/client_golang/prometheus"

	"github.com/FINTLabs/fint-link-walker/go-services/pkg/store"
)

const namespace = "link_walker"

// Collector pulls the latest summary per org from the Store on each
// scrape and emits the per-tenant + per-resource gauges.
type Collector struct {
	Store  store.Store
	Logger *slog.Logger

	orgIntegrity *prometheus.Desc
	resIntegrity *prometheus.Desc
	resRecords   *prometheus.Desc
	resRefs      *prometheus.Desc
	resBroken    *prometheus.Desc
}

func NewCollector(s store.Store, logger *slog.Logger) *Collector {
	return &Collector{
		Store:  s,
		Logger: logger,
		orgIntegrity: prometheus.NewDesc(
			namespace+"_org_integrity_percent",
			"Overall link integrity percent per orgId",
			[]string{"orgId"}, nil,
		),
		resIntegrity: prometheus.NewDesc(
			namespace+"_integrity_percent",
			"Link integrity percent per (orgId, component, resource)",
			[]string{"orgId", "component", "resource"}, nil,
		),
		resRecords: prometheus.NewDesc(
			namespace+"_records_count",
			"Records scanned per (orgId, component, resource)",
			[]string{"orgId", "component", "resource"}, nil,
		),
		resRefs: prometheus.NewDesc(
			namespace+"_refs_count",
			"Outbound refs per (orgId, component, resource)",
			[]string{"orgId", "component", "resource"}, nil,
		),
		resBroken: prometheus.NewDesc(
			namespace+"_broken_links",
			"Broken-link counts per (orgId, component, resource, problem_type)",
			[]string{"orgId", "component", "resource", "problem_type"}, nil,
		),
	}
}

// Describe implements prometheus.Collector.
func (c *Collector) Describe(ch chan<- *prometheus.Desc) {
	ch <- c.orgIntegrity
	ch <- c.resIntegrity
	ch <- c.resRecords
	ch <- c.resRefs
	ch <- c.resBroken
}

// Collect implements prometheus.Collector. Called on every /metrics
// scrape. A failed Store call logs a warning and emits nothing this
// scrape — Prometheus treats absent metrics as gaps in the time series,
// not as zero, so transient store outages won't pollute dashboards.
func (c *Collector) Collect(ch chan<- prometheus.Metric) {
	summaries, err := c.Store.ListSummaries()
	if err != nil {
		c.Logger.Warn("metrics: ListSummaries failed", "err", err)
		return
	}
	for _, ls := range summaries {
		ch <- prometheus.MustNewConstMetric(
			c.orgIntegrity, prometheus.GaugeValue,
			ls.Summary.IntegrityPercent,
			ls.OrgID,
		)
		for _, comp := range ls.Summary.Components {
			for _, res := range comp.Resources {
				labels := []string{ls.OrgID, comp.Component, res.Resource}
				ch <- prometheus.MustNewConstMetric(
					c.resIntegrity, prometheus.GaugeValue,
					res.IntegrityPercent, labels...,
				)
				ch <- prometheus.MustNewConstMetric(
					c.resRecords, prometheus.GaugeValue,
					float64(res.TotalRecords), labels...,
				)
				ch <- prometheus.MustNewConstMetric(
					c.resRefs, prometheus.GaugeValue,
					float64(res.TotalRefs), labels...,
				)
				for problemType, count := range res.ByProblemType {
					ch <- prometheus.MustNewConstMetric(
						c.resBroken, prometheus.GaugeValue,
						float64(count),
						ls.OrgID, comp.Component, res.Resource, problemType,
					)
				}
			}
		}
	}
}
