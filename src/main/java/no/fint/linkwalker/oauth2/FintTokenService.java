package no.fint.linkwalker.oauth2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.fint.portal.config.LdapConfiguration;
import no.fint.portal.model.client.Client;
import no.fint.portal.model.client.ClientService;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
@Import(LdapConfiguration.class)
@ComponentScan(basePackages = "no.fint.portal")
public class FintTokenService {

    private final Map<String, AccessToken> accessTokenMap = new ConcurrentHashMap<>();
    private final ClientService clientService;
    private final WebClient webClient = WebClient.create();

    public String getBearerToken(String clientName, String organisationName) {
        AccessToken accessToken = accessTokenMap.get(clientName);
        boolean tokenIsExpired = accessToken == null || System.currentTimeMillis() > accessToken.getExpirationTime();

        if (tokenIsExpired) {
            Client client = clientService.getClient(clientName, organisationName).orElseThrow(SecurityException::new);
            createNewAccessToken(client).subscribe(newAccessToken -> accessTokenMap.put(clientName, newAccessToken));
        }

        return accessTokenMap.get(clientName).getAccessToken();
    }

    private String resetPassword(Client client) {
        String password = UUID.randomUUID().toString();
        clientService.resetClientPassword(client, password);
        return password;
    }

    private Mono<AccessToken> createNewAccessToken(Client client) {
        return webClient.post()
                .uri("https://idp.felleskomponent.no/nidp/oauth/nam/token")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .body(BodyInserters
                        .fromFormData("grant_type", "password")
                        .with("client_id", client.getClientId())
                        .with("client_secret", clientService.getClientSecret(client))
                        .with("username", client.getName())
                        .with("password", resetPassword(client))
                        .with("scope", "fint-client"))
                .retrieve()
                .bodyToMono(AccessToken.class)
                .doOnNext(this::setExpirationTime);
    }

    private void setExpirationTime(AccessToken accessToken) {
        long bufferTime = 60 * 1000;
        long expirationTime = System.currentTimeMillis() + (accessToken.getExpiresIn() * 1000) - bufferTime;
        accessToken.setExpirationTime(expirationTime);
    }

}
