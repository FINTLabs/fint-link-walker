package no.fintlabs.linkwalker.auth.model;

import java.util.List;

public record AuthObject(
        String dn,
        String name,
        String shortDescription,
        String assetId,
        String asset,
        String note,
        String password,
        String clientSecret,
        String publicKey,
        String clientId,
        List<String> components,
        List<String> accessPackages,
        boolean managed
) {
}
