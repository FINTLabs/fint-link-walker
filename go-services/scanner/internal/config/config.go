// Package config loads scanner configuration from environment variables.
package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	OrgID       string   // e.g. "ofk-no" — labels reports
	BaseURL     string   // FINT API base, e.g. https://api.felleskomponent.no
	Components  []string // component names to scan, e.g. ["utdanning-elev"]
	DatabaseURL string   // Postgres DSN
	FlaisURL    string   // FINT customer-objects gateway base URL
	IdPURL      string   // IdP token endpoint
	PageSize    int      // records per paginated request
	HTTPTimeout time.Duration
}

// Load reads the configuration from environment variables and validates
// it. Missing required fields return an error.
func Load() (*Config, error) {
	c := &Config{
		OrgID:       os.Getenv("ORG_ID"),
		BaseURL:     envOr("BASE_URL", "https://api.felleskomponent.no"),
		Components:  splitCSV(os.Getenv("COMPONENTS")),
		DatabaseURL: envOr("DATABASE_URL", "postgres://linkwalker:linkwalker@localhost:5432/linkwalker?sslmode=disable"),
		FlaisURL:    envOr("FLAIS_GATEWAY_URL", "http://fint-customer-objects-gateway.flais-io.svc.cluster.local:8080"),
		IdPURL:      envOr("IDP_URL", "https://idp.felleskomponent.no/nidp/oauth/nam/token"),
		PageSize:    intOr("PAGE_SIZE", 10000),
		HTTPTimeout: durationOr("HTTP_TIMEOUT", 60*time.Second),
	}
	if c.OrgID == "" {
		return nil, fmt.Errorf("config: ORG_ID is required")
	}
	if len(c.Components) == 0 {
		return nil, fmt.Errorf("config: COMPONENTS is required (comma-separated, e.g. utdanning-elev,utdanning-vurdering)")
	}
	return c, nil
}

func envOr(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func intOr(key string, def int) int {
	v := os.Getenv(key)
	if v == "" {
		return def
	}
	n, err := strconv.Atoi(v)
	if err != nil || n <= 0 {
		return def
	}
	return n
}

func durationOr(key string, def time.Duration) time.Duration {
	v := os.Getenv(key)
	if v == "" {
		return def
	}
	d, err := time.ParseDuration(v)
	if err != nil {
		return def
	}
	return d
}

func splitCSV(s string) []string {
	if s == "" {
		return nil
	}
	parts := strings.Split(s, ",")
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		if t := strings.TrimSpace(p); t != "" {
			out = append(out, t)
		}
	}
	return out
}
