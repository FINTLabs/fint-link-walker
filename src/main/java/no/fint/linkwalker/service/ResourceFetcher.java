package no.fint.linkwalker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.fint.linkwalker.data.PwfUtils;
import no.fint.linkwalker.oauth2.FintTokenService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerErrorException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.function.Consumer;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceFetcher {

    private static final int RETRY_LIMIT = 3;
    private static final long INITIAL_DELAY = 100;
    private static final long MULTIPLIER = 2;

    private final WebClient webClient = WebClient.create();
    private final FintTokenService fintTokenService;

    public <T> Mono<ResponseEntity<T>> fetchResource(String organisation, String client, String location, HttpHeaders headers, Class<T> type) {
        return webClient
                .get()
                .uri(location)
                .headers(addHeaders(headers, location, client, organisation))
                .retrieve()
                .toEntity(type)
                .retryWhen(Retry.backoff(RETRY_LIMIT, Duration.ofMillis(INITIAL_DELAY))
                        .filter(throwable -> throwable instanceof ServerErrorException)
                        .doBeforeRetry(retrySignal -> log.info("Retry {}/{} due to 5xx server error. Waiting {} ms before retrying...",
                                retrySignal.totalRetries() + 1, RETRY_LIMIT, INITIAL_DELAY * (long) Math.pow(MULTIPLIER, retrySignal.totalRetries()))))
                .doOnError(e -> log.error("Failed to fetch resource: " + e.getMessage()))
                .onErrorResume(this::handleError);
    }

    private <T> Mono<? extends ResponseEntity<T>> handleError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException webClientResponseException) {
            return Mono.just(ResponseEntity.status(webClientResponseException.getStatusCode()).build());
        }
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
    }

    private Consumer<HttpHeaders> addHeaders(HttpHeaders headers, String location, String client, String organisation) {
        return httpHeaders -> {
            httpHeaders.addAll(headers);
            if (!PwfUtils.isPwf(location)) {
                httpHeaders.add(AUTHORIZATION, "Bearer " + fintTokenService.getBearerToken(client, organisation));
            }
        };
    }

}
