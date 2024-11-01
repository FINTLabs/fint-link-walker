package no.fintlabs.linkwalker.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.fintlabs.linkwalker.auth.model.AuthObject;
import no.fintlabs.linkwalker.auth.model.AuthResponse;
import no.fintlabs.linkwalker.auth.model.TokenResponse;
import no.fintlabs.linkwalker.task.model.Task;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final WebClient gatewayWebClient;
    private final WebClient idpWebClient;

    public void applyNewAccessToken(Task task) {
        String client = task.getClient().toLowerCase();

        getAuthResponse(task.getOrg(), client)
                .subscribe(authResponse -> resetPassword(authResponse)
                        .subscribe(resetAuthResponse -> decryptAuthResponse(client, resetAuthResponse)
                                .subscribe(decryptedResponse -> getTokenResponse(decryptedResponse)
                                        .subscribe(tokenResponse -> task.setToken(tokenResponse.accesToken())))));
    }

    private Mono<TokenResponse> getTokenResponse(AuthObject decryptedAuthObject) {
        return idpWebClient.post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(createFormData(decryptedAuthObject)))
                .retrieve()
                .bodyToMono(TokenResponse.class);
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

    private Mono<AuthResponse> resetPassword(AuthResponse authResponse) {
        return gatewayWebClient.post()
                .uri("/client/password/reset")
                .bodyValue(authResponse)
                .retrieve()
                .bodyToMono(AuthResponse.class);
    }

    private Mono<AuthObject> decryptAuthResponse(String clientName, AuthResponse authResponse) {
        return gatewayWebClient.post()
                .uri(createDecryptUri(clientName))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(authResponse)
                .retrieve()
                .bodyToMono(AuthObject.class);
    }

    private Mono<AuthResponse> getAuthResponse(String orgName, String clientName) {
        return gatewayWebClient.get()
                .uri(createUri(orgName, clientName))
                .retrieve()
                .bodyToMono(AuthResponse.class);
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
