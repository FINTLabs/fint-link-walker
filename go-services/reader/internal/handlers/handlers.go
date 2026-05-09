// Package handlers exposes the reader's HTTP surface: per-org scan
// summary and paginated, filterable rows. Reads from a Store; the
// scanner is the producer.
package handlers

import (
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"regexp"
	"strconv"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"

	"github.com/FINTLabs/fint-link-walker/go-services/pkg/report"
	"github.com/FINTLabs/fint-link-walker/go-services/pkg/store"
)

// orgIDPattern matches the same shape the Kotlin reader accepts:
// lowercase letters, digits, underscores. Hyphens reach the scanner
// (env config) but get persisted in the same form they arrive in.
var orgIDPattern = regexp.MustCompile(`^[a-z0-9_-]+$`)

type Handlers struct {
	Store     store.Store
	Logger    *slog.Logger
	Collector prometheus.Collector // optional; if set, /metrics is served from it
}

func (h *Handlers) Register(mux *http.ServeMux) {
	mux.HandleFunc("GET /report/{orgId}/summary", h.summary)
	mux.HandleFunc("GET /report/{orgId}/rows", h.rows)
	mux.HandleFunc("GET /reports", h.listSummaries)
	mux.HandleFunc("GET /healthz", h.health)

	if h.Collector != nil {
		// Use a private registry so we expose only the link-walker
		// metrics — none of the default Go runtime / process gauges.
		// Match the Kotlin reader's /actuator/prometheus output, which
		// only carried the link_walker_* gauges that mattered to the
		// dashboards.
		reg := prometheus.NewRegistry()
		reg.MustRegister(h.Collector)
		mux.Handle("GET /metrics", promhttp.HandlerFor(reg, promhttp.HandlerOpts{}))
	}
}

func (h *Handlers) summary(w http.ResponseWriter, req *http.Request) {
	orgID := req.PathValue("orgId")
	if !orgIDPattern.MatchString(orgID) {
		http.Error(w, "invalid orgId", http.StatusBadRequest)
		return
	}
	s, err := h.Store.GetSummary(orgID)
	if err != nil {
		h.respondErr(w, err)
		return
	}
	writeJSON(w, http.StatusOK, s)
}

func (h *Handlers) rows(w http.ResponseWriter, req *http.Request) {
	orgID := req.PathValue("orgId")
	if !orgIDPattern.MatchString(orgID) {
		http.Error(w, "invalid orgId", http.StatusBadRequest)
		return
	}
	q := req.URL.Query()
	page, err := atoiOr(q.Get("page"), 0)
	if err != nil {
		http.Error(w, "page must be a number", http.StatusBadRequest)
		return
	}
	size, err := atoiOr(q.Get("size"), 100)
	if err != nil {
		http.Error(w, "size must be a number", http.StatusBadRequest)
		return
	}
	filter := report.RowFilter{
		Component:   q.Get("component"),
		Resource:    q.Get("resource"),
		ProblemType: q.Get("problemType"),
	}
	res, err := h.Store.FindRows(orgID, filter, page, size)
	if err != nil {
		h.respondErr(w, err)
		return
	}
	writeJSON(w, http.StatusOK, res)
}

func (h *Handlers) listSummaries(w http.ResponseWriter, _ *http.Request) {
	all, err := h.Store.ListSummaries()
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, all)
}

func (h *Handlers) health(w http.ResponseWriter, _ *http.Request) {
	w.WriteHeader(http.StatusOK)
}

func (h *Handlers) respondErr(w http.ResponseWriter, err error) {
	var nf store.ErrNotFound
	if errors.As(err, &nf) {
		http.Error(w, nf.Error(), http.StatusNotFound)
		return
	}
	h.Logger.Warn("store error", "err", err)
	http.Error(w, err.Error(), http.StatusInternalServerError)
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}

func atoiOr(s string, def int) (int, error) {
	if s == "" {
		return def, nil
	}
	return strconv.Atoi(s)
}
