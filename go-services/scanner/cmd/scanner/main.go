// Scanner walks every link in every configured FINT component for one
// organization, builds a Report, and publishes it via the configured
// store. Bearer tokens come from the FLAIS gateway → IdP password-grant
// flow — same as the Kotlin scanner.
package main

import (
	"context"
	"log/slog"
	"net/http"
	"os"
	"strings"
	"time"

	_ "github.com/FINTLabs/fint-link-walker/go-services/internal/registerall"
	"github.com/FINTLabs/fint-link-walker/go-services/pkg/auth"
	"github.com/FINTLabs/fint-link-walker/go-services/pkg/store/postgres"
	"github.com/FINTLabs/fint-link-walker/go-services/scanner/internal/config"
	"github.com/FINTLabs/fint-link-walker/go-services/scanner/internal/fintapi"
	"github.com/FINTLabs/fint-link-walker/go-services/scanner/internal/scan"
)

func main() {
	startup := time.Now()
	logger := slog.New(slog.NewJSONHandler(os.Stderr, &slog.HandlerOptions{
		Level: parseLogLevel(os.Getenv("LOG_LEVEL")),
	}))

	cfg, err := config.Load()
	if err != nil {
		logger.Error("config", "err", err)
		os.Exit(2)
	}
	configMs := time.Since(startup).Milliseconds()

	ctx := context.Background()

	// Postgres-backed report store.
	storeStart := time.Now()
	st, err := postgres.New(ctx, cfg.DatabaseURL)
	if err != nil {
		logger.Error("store", "err", err)
		os.Exit(1)
	}
	defer st.Close()
	storeMs := time.Since(storeStart).Milliseconds()

	// Auth: FLAIS gateway → IdP → bearer token.
	httpClient := &http.Client{Timeout: cfg.HTTPTimeout}
	authSvc := auth.NewService(
		auth.NewFlaisClient(cfg.FlaisURL, cfg.Components, httpClient),
		auth.NewIdPClient(cfg.IdPURL, httpClient),
	)
	authStart := time.Now()
	bearer, err := authSvc.GetBearerToken(ctx, cfg.OrgID)
	if err != nil {
		logger.Error("auth", "err", err)
		os.Exit(1)
	}
	authMs := time.Since(authStart).Milliseconds()

	logger.Info("startup complete",
		"orgId", cfg.OrgID,
		"configMs", configMs,
		"storeMs", storeMs,
		"authMs", authMs,
		"totalMs", time.Since(startup).Milliseconds(),
	)

	scanner := &scan.Scanner{
		OrgID:           cfg.OrgID,
		Components:      cfg.Components,
		PageSize:        cfg.PageSize,
		API:             fintapi.New(cfg.BaseURL, bearer, cfg.HTTPTimeout),
		Store:           st,
		Logger:          logger,
		HeapProfilePath: os.Getenv("HEAP_PROFILE"), // optional; per-phase dumps from inside Run
	}

	if _, err := scanner.Run(ctx); err != nil {
		logger.Error("scan", "err", err)
		os.Exit(1)
	}
}

// parseLogLevel maps LOG_LEVEL=debug|info|warn|error to slog levels.
// Default is info. debug surfaces per-type fetch / extract timings.
func parseLogLevel(s string) slog.Level {
	switch strings.ToLower(strings.TrimSpace(s)) {
	case "debug":
		return slog.LevelDebug
	case "warn", "warning":
		return slog.LevelWarn
	case "error":
		return slog.LevelError
	default:
		return slog.LevelInfo
	}
}
