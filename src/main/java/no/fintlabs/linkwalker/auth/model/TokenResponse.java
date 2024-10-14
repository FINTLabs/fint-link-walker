package no.fintlabs.linkwalker.auth.model;

public record TokenResponse (
        String accesToken,
        String tokenType,
        int expiresIn,
        String acr,
        String scope
) {
}
