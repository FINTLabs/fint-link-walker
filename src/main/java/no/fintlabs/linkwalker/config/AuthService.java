package no.fintlabs.linkwalker.config;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class AuthService {

    private final WebClient gatewayWebClient;
    private final WebClient idpWebClient;

    public AuthService(WebClient gatewayWebClient, WebClient idpWebClient) {
        this.gatewayWebClient = gatewayWebClient;
        this.idpWebClient = idpWebClient;
    }

    public String getNewAccessToken(String orgName, String clientName) throws Exception {
        AuthResponse decryptAuthResponse = decryptAuthResponse(clientName, getAuthResponse(orgName, clientName));
        return getTokenResponse(decryptAuthResponse).accesToken();
    }

    private TokenResponse getTokenResponse(AuthResponse decryptAuthResponse) throws Exception {
        return idpWebClient.post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(createFormData(decryptAuthResponse.object())))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .block();
    }

    private MultiValueMap<String, String> createFormData(AuthObject authObject) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("client_id", authObject.clientId());
        formData.add("client_secret", authObject.clientSecret());
        formData.add("username", authObject.name());
        formData.add("password", authObject.password());
        formData.add("scope", createScope(authObject.name()));
        return formData;
    }

    private String createScope(String name) {
        if (name.contains("@adapter")) {
            return "fint-adapter";
        } else {
            return "fint-client";
        }
    }

    private AuthResponse decryptAuthResponse(String clientName, AuthResponse authResponse) throws Exception {
        return gatewayWebClient.post()
                .uri(createDecryptUri(clientName))
                .bodyValue(authResponse)
                .retrieve()
                .bodyToMono(AuthResponse.class)
                .block();
    }

    private AuthResponse getAuthResponse(String orgName, String clientName) throws Exception {
        return gatewayWebClient.get()
                .uri(createUri(orgName, clientName))
                .retrieve()
                .bodyToMono(AuthResponse.class)
                .block();
    }

    private String createUri(String orgName, String clientName) {
        String formattedOrgName = orgName.replace(".", "_");
        if (clientName.contains("@adapter")) {
            return "/adapter/cn=" + clientName + ",ou=adapters,ou=" + formattedOrgName + ",ou=organisations,o=fint";
        } else {
            return "/client/cn=" + clientName + ",ou=clients,ou=" + formattedOrgName + ",ou=organisations,o=fint";
        }
    }

    private String createDecryptUri(String clientName) {
        if (clientName.contains("@adapter")) {
            return "/adapter/decrypt";
        } else {
            return "/client/decrypt";
        }
    }
}
