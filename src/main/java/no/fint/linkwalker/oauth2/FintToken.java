package no.fint.linkwalker.oauth2;

import lombok.Getter;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

@Getter
public class FintToken {

    private String issuer;
    private String jwtId;
    private String audience;
    private Instant expirationTime;
    private Instant issuedAtTime;
    private Instant notBeforeTime;
    private String subject;
    private String privateKey;
    private List<String> scope;
    private String fintAssetName;
    private String fintAssetIDs;
    private List<String> roles;
    private String commonName;

    public FintToken(Jwt jwt) {
        issuer = jwt.getClaimAsString("iss");
        jwtId = jwt.getClaimAsString("jti");
        audience = jwt.getClaimAsString("aud");
        expirationTime = jwt.getExpiresAt();
        issuedAtTime = jwt.getIssuedAt();
        notBeforeTime = jwt.getNotBefore();
        subject = jwt.getSubject();
        privateKey = jwt.getClaimAsString("_pvt");
        scope = jwt.getClaimAsStringList("scope");
        fintAssetName = jwt.getClaimAsString("fintAssetName");
        fintAssetIDs = jwt.getClaimAsString("fintAssetIDs");
        roles = jwt.getClaimAsStringList("Roles");
        commonName = jwt.getClaimAsString("cn");
    }

}
