package auth

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
)

// IdPClient exchanges a decrypted AuthObject for an OAuth bearer token
// via the FINT IdP's password-grant endpoint.
//
// In production: https://idp.felleskomponent.no/nidp/oauth/nam/token
type IdPClient struct {
	TokenURL string
	HTTP     *http.Client
}

func NewIdPClient(tokenURL string, httpClient *http.Client) *IdPClient {
	if httpClient == nil {
		httpClient = http.DefaultClient
	}
	return &IdPClient{
		TokenURL: tokenURL,
		HTTP:     httpClient,
	}
}

// GetToken does the password grant: client_id + client_secret +
// username + password → access_token. Returns the parsed TokenResponse.
func (c *IdPClient) GetToken(ctx context.Context, obj *AuthObject) (*TokenResponse, error) {
	form := url.Values{}
	form.Set("grant_type", "password")
	form.Set("client_id", obj.ClientID)
	form.Set("client_secret", obj.ClientSecret)
	form.Set("username", obj.Name)
	form.Set("password", obj.Password)
	form.Set("scope", "fint-client")

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.TokenURL, strings.NewReader(form.Encode()))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.Header.Set("Accept", "application/json")

	resp, err := c.HTTP.Do(req)
	if err != nil {
		return nil, fmt.Errorf("idp: token request: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode/100 != 2 {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 1024))
		return nil, fmt.Errorf("idp: status %d: %s", resp.StatusCode, strings.TrimSpace(string(body)))
	}
	var tr TokenResponse
	if err := json.NewDecoder(resp.Body).Decode(&tr); err != nil {
		return nil, fmt.Errorf("idp: decode token: %w", err)
	}
	if tr.AccessToken == "" {
		return nil, fmt.Errorf("idp: empty access_token")
	}
	return &tr, nil
}
