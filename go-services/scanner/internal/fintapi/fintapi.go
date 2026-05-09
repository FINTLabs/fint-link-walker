// Package fintapi is a thin HTTP client for the FINT consumer API.
// Three calls used by the scanner:
//
//	GET /{path}                      — full collection (no pagination)
//	GET /{path}/cache/size           — total item count for a type
//	GET /{path}?size=N&offset=M     — paginated page
//
// All requests carry the bearer token and the V4 model-version header.
package fintapi

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

type Client struct {
	BaseURL string
	Bearer  string
	HTTP    *http.Client
}

// New constructs a Client. The bearer is captured at construction; if it
// expires mid-scan you'll see 401s on subsequent requests — POC doesn't
// refresh.
func New(baseURL, bearer string, timeout time.Duration) *Client {
	return &Client{
		BaseURL: strings.TrimRight(baseURL, "/"),
		Bearer:  bearer,
		HTTP:    &http.Client{Timeout: timeout},
	}
}

// Get retrieves a HAL collection at path with no pagination. Caller
// owns the body and must close it.
func (c *Client) Get(ctx context.Context, path string) (io.ReadCloser, error) {
	return c.do(ctx, c.url(path))
}

// Page retrieves one offset-based page of records.
func (c *Client) Page(ctx context.Context, path string, size, offset int) (io.ReadCloser, error) {
	return c.do(ctx, fmt.Sprintf("%s?size=%d&offset=%d", c.url(path), size, offset))
}

// CacheSize returns the total record count for the resource at path.
// Uses fint-core-consumer's /{path}/cache/size endpoint.
func (c *Client) CacheSize(ctx context.Context, path string) (int, error) {
	body, err := c.do(ctx, c.url(path)+"/cache/size")
	if err != nil {
		return 0, err
	}
	defer body.Close()
	var resp struct {
		Size int `json:"size"`
	}
	if err := json.NewDecoder(body).Decode(&resp); err != nil {
		return 0, fmt.Errorf("fintapi: decode cache size for %s: %w", path, err)
	}
	return resp.Size, nil
}

func (c *Client) url(path string) string {
	return c.BaseURL + "/" + strings.TrimLeft(path, "/")
}

func (c *Client) do(ctx context.Context, url string) (io.ReadCloser, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, fmt.Errorf("fintapi: build request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+c.Bearer)
	req.Header.Set("Accept", "application/json")
	req.Header.Set("x-fint-model-version-override", "V4")

	resp, err := c.HTTP.Do(req)
	if err != nil {
		return nil, fmt.Errorf("fintapi: %s: %w", url, err)
	}
	if resp.StatusCode/100 != 2 {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 256))
		resp.Body.Close()
		return nil, fmt.Errorf("fintapi: %s: status %d: %s", url, resp.StatusCode, strings.TrimSpace(string(body)))
	}
	// 200 OK with non-JSON body → no real adapter is registered for this
	// org+component; the request was answered by the fint-test-client
	// fallback (which returns an HTML page). The org has no route for
	// this component — treat it as a known soft-fail with a useful message.
	if ct := resp.Header.Get("Content-Type"); !strings.Contains(strings.ToLower(ct), "json") {
		resp.Body.Close()
		return nil, fmt.Errorf("fintapi: %s: no adapter route for this org/component — request hit fint-test-client fallback (Content-Type=%s)", url, ct)
	}
	return resp.Body, nil
}
