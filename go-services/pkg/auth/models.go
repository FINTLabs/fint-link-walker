// Package auth implements the Kotlin auth flow in Go: look up an
// existing FLAIS-stored client for an org, create one if missing,
// decrypt it, then exchange clientId/clientSecret/username/password for
// a bearer token at the IdP. AuthService.GetBearerToken stitches the
// pieces together.
package auth

import (
	"strings"
)

// ClientName is the FINT client name the scanner registers under.
// Matches AuthConstants.CLIENT_NAME on the Kotlin side.
const ClientName = "link-walker"

// AuthObject is the decrypted client identity FLAIS returns. The
// fields used downstream are name/password (HTTP-Basic-style username +
// password) and clientId/clientSecret (OAuth client credentials) — the
// IdP needs all four for the password-grant flow.
type AuthObject struct {
	DN               string   `json:"dn"`
	Name             string   `json:"name"`
	ShortDescription string   `json:"shortDescription"`
	AssetID          string   `json:"assetId"`
	Asset            string   `json:"asset"`
	Note             string   `json:"note"`
	Password         string   `json:"password"`
	ClientSecret     string   `json:"clientSecret"`
	PublicKey        string   `json:"publicKey"`
	ClientID         string   `json:"clientId"`
	Components       []string `json:"components"`
	AccessPackages   []string `json:"accessPackages"`
	Managed          bool     `json:"managed"`
}

// AuthResponse is FLAIS gateway's envelope. Object is nil when no
// client exists yet — that's the signal to POST /client and create one.
type AuthResponse struct {
	Object       *AuthObject `json:"object"`
	OrgID        string      `json:"orgId"`
	Operation    string      `json:"operation"`
	ErrorMessage *string     `json:"errorMessage,omitempty"`
}

// ClientRequest is the body for POST /client when a client is missing
// and needs to be created. The components list bounds what the new
// client may access.
type ClientRequest struct {
	OrgID  string     `json:"orgId"`
	Object ClientData `json:"object"`
}

type ClientData struct {
	Name             string   `json:"name"`
	ShortDescription string   `json:"shortDescription"`
	Note             string   `json:"note"`
	Managed          bool     `json:"managed"`
	Components       []string `json:"components"`
}

// NewClientRequest builds the body the FLAIS gateway expects when
// creating a fresh client. orgID is supplied in any form (dashes,
// underscores, dots); the body always carries it dot-separated.
// components is the kebab-case form (e.g. "utdanning-elev") — converted
// to the LDAP component DN form FLAIS stores.
func NewClientRequest(orgID string, components []string) ClientRequest {
	return ClientRequest{
		OrgID: dottedOrg(orgID),
		Object: ClientData{
			Name:             ClientName,
			ShortDescription: "Autogenerert relasjontester",
			Note:             "En generert klient for relasjon testing",
			Managed:          true,
			Components:       wrapComponents(components),
		},
	}
}

// TokenResponse is the IdP's reply to a successful password grant. The
// fields beyond access_token are kept for parity but the scanner only
// reads access_token.
type TokenResponse struct {
	AccessToken string `json:"access_token"`
	TokenType   string `json:"token_type"`
	ExpiresIn   int    `json:"expires_in"`
	ACR         string `json:"acr"`
	Scope       string `json:"scope"`
}

// dottedOrg returns the org id in the form "ofk.no" regardless of
// whether it was supplied as "ofk-no", "ofk_no", or "ofk.no".
func dottedOrg(orgID string) string {
	return strings.NewReplacer("-", ".", "_", ".").Replace(orgID)
}

// underscoredOrg returns the org id in the form "ofk_no" — the form
// used inside the LDAP `ou=...` segment.
func underscoredOrg(orgID string) string {
	return strings.NewReplacer("-", "_", ".", "_").Replace(orgID)
}

// wrapComponents converts ["utdanning-elev", "felles-kodeverk"] into
// ["ou=utdanning_elev,ou=components,o=fint", "ou=felles_kodeverk,ou=components,o=fint"]
// — the form FLAIS stores in the client object.
func wrapComponents(components []string) []string {
	out := make([]string, 0, len(components))
	for _, c := range components {
		under := strings.ReplaceAll(c, "-", "_")
		out = append(out, "ou="+under+",ou=components,o=fint")
	}
	return out
}
