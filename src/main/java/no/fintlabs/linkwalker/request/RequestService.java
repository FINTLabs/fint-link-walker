package no.fintlabs.linkwalker.request;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import no.fintlabs.linkwalker.request.model.FintResources;
import no.fintlabs.linkwalker.request.model.TokenResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Service
@RequiredArgsConstructor
public class RequestService {

    private final WebClient webClient;
    private final Cache<String, HttpStatusCode> statusCodeCache;

    public Mono<FintResources> fetchFintResources(String uri, String token) {
        return webClient.get()
                .uri(uri)
                .header(AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(FintResources.class);
    }

    public Mono<HttpStatusCode> fetchStatusCode(String uri, String token) {
        HttpStatusCode cachedStatus = statusCodeCache.getIfPresent(uri);
        if (cachedStatus != null) {
            return Mono.just(cachedStatus);
        }

        return webClient.get()
                .uri(uri)
                .header(AUTHORIZATION, "Bearer " + token)
                .exchangeToMono(response -> {
                    HttpStatusCode statusCode = response.statusCode();
                    statusCodeCache.put(uri, statusCode);
                    return Mono.just(statusCode);
                });
    }

    public Mono<TokenResponse> getToken(String clientName, String password, String clientId, String clientSecret) {
        return webClient.post()
                .uri("https://idp.felleskomponent.no/nidp/oauth/nam/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "password")
                        .with("username", clientName)
                        .with("password", password)
                        .with("client_id", clientId)
                        .with("client_secret", clientSecret)
                        .with("scope", "fint-client"))
                .retrieve()
                .bodyToMono(TokenResponse.class);

    }

}
