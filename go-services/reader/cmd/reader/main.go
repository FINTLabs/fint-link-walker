// Reader is a tiny HTTP service that serves scan reports the scanner
// publishes to Postgres. Two endpoints: /report/{orgId}/summary and
// /report/{orgId}/rows.
package main

import (
	"context"
	"log/slog"
	"net/http"
	"os"
	"time"

	"github.com/FINTLabs/fint-link-walker/go-services/pkg/store/postgres"
	"github.com/FINTLabs/fint-link-walker/go-services/reader/internal/handlers"
	"github.com/FINTLabs/fint-link-walker/go-services/reader/internal/metrics"
)

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))

	addr := envOr("READER_ADDR", ":8080")
	dsn := envOr("DATABASE_URL", "postgres://linkwalker:linkwalker@localhost:5432/linkwalker?sslmode=disable")

	ctx := context.Background()
	st, err := postgres.New(ctx, dsn)
	if err != nil {
		logger.Error("store", "err", err)
		os.Exit(1)
	}
	defer st.Close()

	h := &handlers.Handlers{
		Store:     st,
		Logger:    logger,
		Collector: metrics.NewCollector(st, logger),
	}
	mux := http.NewServeMux()
	h.Register(mux)

	srv := &http.Server{
		Addr:              addr,
		Handler:           mux,
		ReadHeaderTimeout: 5 * time.Second,
	}

	logger.Info("reader starting", "addr", addr)
	if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		logger.Error("reader stopped", "err", err)
		os.Exit(1)
	}
}

func envOr(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}
