package no.fint.linkwalker.oauth2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.fint.portal.model.client.Client;
import no.fint.portal.model.client.ClientService;
import no.fintlabs.core.resource.server.security.converter.CorePrincipalConverter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReactiveOauth2Factory {

    private final CorePrincipalConverter converter = new CorePrincipalConverter();
    private final JwtDecoder jwtDecoder;
    private final ClientService clientService;
    private final ServerOAuth2AuthorizedClientRepository authorizedClientRepository;
    private final Map<String, String> registrationIdMap = new HashMap<>();
    private final WebClient webClient = WebClient.create();

    // TODO: Handle bug scenario
    // A bug may occur if a client's password is reset after it's been registered by the service.
    public void registerNewClient(String clientName, String organisationName, ServerWebExchange serverWebExchange) {
        if (registrationIdMap.containsKey(clientName)) {
            return;
        }

        String registrationId = UUID.randomUUID().toString();
        registrationIdMap.put(clientName, registrationId);

        Client client = clientService.getClient(clientName, organisationName).orElseThrow(SecurityException::new);
        ClientRegistration clientRegistration = createClientRegistration(client, registrationId);
        createOuath2AuthorizedClient(clientRegistration, client, serverWebExchange);
    }

    private ClientRegistration createClientRegistration(Client client, String registrationId) {
        return ClientRegistration.withRegistrationId(registrationId)
                .clientName(client.getName())
                .clientId(client.getClientId())
                .clientSecret(clientService.getClientSecret(client))
                .tokenUri("https://idp.felleskomponent.no/nidp/oauth/nam/token")
                .authorizationGrantType(AuthorizationGrantType.PASSWORD)
                .scope("fint-client")
                .build();
    }

    private String resetPassword(Client client) {
        String password = UUID.randomUUID().toString();
        clientService.resetClientPassword(client, password);
        return password;
    }

    private Mono<AccessToken> getAccessToken(Client client) {
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
                .bodyToMono(AccessToken.class);
    }

    private void createOuath2AuthorizedClient(ClientRegistration clientRegistration, Client client, ServerWebExchange serverWebExchange) {
        getAccessToken(client).subscribe(fintAccessToken -> {
            Jwt jwt = jwtDecoder.decode("bearer " + fintAccessToken.getAccessToken());
            FintToken fintToken = new FintToken(jwt);

            OAuth2AccessToken oAuth2AccessToken = new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    fintAccessToken.getAccessToken(),
                    fintToken.getIssuedAtTime(),
                    fintToken.getExpirationTime()
            );

            OAuth2AuthorizedClient oAuth2AuthorizedClient = new OAuth2AuthorizedClient(
                    clientRegistration,
                    client.getName(),
                    oAuth2AccessToken
            );

            Objects.requireNonNull(converter.convert(jwt))
                    .doOnError(throwable -> log.error(throwable.getMessage()))
                    .subscribe(authentication -> {
                authorizedClientRepository.saveAuthorizedClient(oAuth2AuthorizedClient, authentication, serverWebExchange).subscribe();
            });
        });
    }

}
