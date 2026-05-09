package auth

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
)

// FlaisClient talks to the FINT customer-objects gateway: look up a
// client by org id, create one if missing, decrypt it.
//
// In production the gateway URL is the in-cluster service
// (http://fint-customer-objects-gateway.flais-io.svc.cluster.local:8080).
// For local dev, kubectl-port-forward to that service and point BaseURL
// at the forwarded port (e.g. http://localhost:64130).
type FlaisClient struct {
	BaseURL    string
	Components []string // for client creation if no client exists for the org
	HTTP       *http.Client
}

func NewFlaisClient(baseURL string, components []string, httpClient *http.Client) *FlaisClient {
	if httpClient == nil {
		httpClient = http.DefaultClient
	}
	return &FlaisClient{
		BaseURL:    strings.TrimRight(baseURL, "/"),
		Components: components,
		HTTP:       httpClient,
	}
}

// GetAuthObject does the full lookup → maybe-create → decrypt dance and
// returns the plaintext AuthObject for the org.
func (c *FlaisClient) GetAuthObject(ctx context.Context, orgID string) (*AuthObject, error) {
	encrypted, err := c.lookup(ctx, orgID)
	if err != nil {
		return nil, fmt.Errorf("flais: lookup %s: %w", orgID, err)
	}
	if encrypted.Object == nil {
		encrypted, err = c.create(ctx, orgID)
		if err != nil {
			return nil, fmt.Errorf("flais: create %s: %w", orgID, err)
		}
	}
	decrypted, err := c.decrypt(ctx, encrypted)
	if err != nil {
		return nil, fmt.Errorf("flais: decrypt %s: %w", orgID, err)
	}
	return decrypted, nil
}

func (c *FlaisClient) lookup(ctx context.Context, orgID string) (*AuthResponse, error) {
	uri := c.BaseURL + lookupPath(orgID)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, uri, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Accept", "application/json")

	var resp AuthResponse
	if err := c.do(req, &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

func (c *FlaisClient) create(ctx context.Context, orgID string) (*AuthResponse, error) {
	body, err := json.Marshal(NewClientRequest(orgID, c.Components))
	if err != nil {
		return nil, fmt.Errorf("marshal client request: %w", err)
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.BaseURL+"/client", bytes.NewReader(body))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")

	var resp AuthResponse
	if err := c.do(req, &resp); err != nil {
		return nil, err
	}
	if resp.Object == nil {
		return nil, fmt.Errorf("create returned no auth object (operation=%s)", resp.Operation)
	}
	return &resp, nil
}

func (c *FlaisClient) decrypt(ctx context.Context, encrypted *AuthResponse) (*AuthObject, error) {
	body, err := json.Marshal(encrypted)
	if err != nil {
		return nil, fmt.Errorf("marshal decrypt request: %w", err)
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.BaseURL+"/client/decrypt", bytes.NewReader(body))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")

	var obj AuthObject
	if err := c.do(req, &obj); err != nil {
		return nil, err
	}
	return &obj, nil
}

func (c *FlaisClient) do(req *http.Request, out any) error {
	resp, err := c.HTTP.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode/100 != 2 {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 1024))
		return fmt.Errorf("status %d: %s", resp.StatusCode, strings.TrimSpace(string(body)))
	}
	return json.NewDecoder(resp.Body).Decode(out)
}

// lookupPath builds the FLAIS GET path for orgID. Mirrors
// FlaisGateway.createUri / createCn on the Kotlin side.
//
// Example: "ofk-no" →
//
//	/client/cn=link-walker@client.ofk.no,ou=clients,ou=ofk_no,ou=organisations,o=fint
func lookupPath(orgID string) string {
	cn := ClientName + "@client." + dottedOrg(orgID)
	return "/client/cn=" + cn + ",ou=clients,ou=" + underscoredOrg(orgID) + ",ou=organisations,o=fint"
}
