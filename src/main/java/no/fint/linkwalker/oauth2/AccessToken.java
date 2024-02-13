package no.fint.linkwalker.oauth2;

import lombok.Data;

@Data
public class AccessToken {

    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private String scope;
    private String acr;

}
