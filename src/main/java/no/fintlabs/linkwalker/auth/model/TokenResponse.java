package no.fintlabs.linkwalker.auth.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TokenResponse (
        @JsonProperty("access_token") String accesToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") int expiresIn,
        String acr,
        String scope
) {
}
