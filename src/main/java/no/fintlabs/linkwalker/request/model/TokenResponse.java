package no.fintlabs.linkwalker.request.model;

public record TokenResponse(String access_token, String token_type, int expires_in, String acr) {
}
