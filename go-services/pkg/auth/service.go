package auth

import (
	"context"
	"fmt"
)

// Service stitches FLAIS gateway lookups + IdP token exchange into a
// single GetBearerToken(orgID) call. Mirrors AuthService on the Kotlin
// side; not goroutine-safe in the sense of "you can hammer this from
// many goroutines" — for now the scanner runs sequentially.
type Service struct {
	Flais *FlaisClient
	IdP   *IdPClient
}

func NewService(flais *FlaisClient, idp *IdPClient) *Service {
	return &Service{Flais: flais, IdP: idp}
}

// GetBearerToken returns a usable bearer token for the given orgID.
// It looks up the FLAIS-stored client (creating one if absent),
// decrypts it, and exchanges its credentials at the IdP.
func (s *Service) GetBearerToken(ctx context.Context, orgID string) (string, error) {
	obj, err := s.Flais.GetAuthObject(ctx, orgID)
	if err != nil {
		return "", err
	}
	tok, err := s.IdP.GetToken(ctx, obj)
	if err != nil {
		return "", fmt.Errorf("auth: %w", err)
	}
	return tok.AccessToken, nil
}
