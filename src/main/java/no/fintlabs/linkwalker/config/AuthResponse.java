package no.fintlabs.linkwalker.config;

public record AuthResponse(
        AuthObject object,
        String orgId,
        String operation

) {
}
