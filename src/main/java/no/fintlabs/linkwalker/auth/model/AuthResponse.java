package no.fintlabs.linkwalker.auth.model;

import no.fintlabs.linkwalker.auth.model.AuthObject;

public record AuthResponse(
        AuthObject object,
        String orgId,
        String operation
) {
}
