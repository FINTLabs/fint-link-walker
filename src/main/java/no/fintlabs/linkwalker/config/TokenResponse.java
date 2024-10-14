package no.fintlabs.linkwalker.config;

public record TokenResponse (
        String accesToken,
        String tokenType,
        int expiresIn,
        String acr,
        String scope
) {
}
