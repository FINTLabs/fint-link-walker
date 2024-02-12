package no.fint.linkwalker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.fint.linkwalker.RestTemplateProvider;
import no.fint.linkwalker.data.PwfUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerErrorException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

import static no.fint.linkwalker.data.Constants.PWF_BASE_URL;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceFetcher {

    private static final int RETRY_LIMIT = 3;
    private static final long INITIAL_DELAY = 100;
    private static final long MULTIPLIER = 2;

    private final RestTemplateProvider restTemplateProvider;

    public <T> Mono<ResponseEntity<T>> fetchResource(String organisation, String client, String location, HttpHeaders headers, Class<T> type) {
        return getWebClient(organisation, client, location)
                .get()
                .uri(location)
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .retrieve()
                .toEntity(type)
                .retryWhen(Retry.backoff(RETRY_LIMIT, Duration.ofMillis(INITIAL_DELAY))
                        .filter(throwable -> throwable instanceof ServerErrorException)
                        .doBeforeRetry(retrySignal -> log.info("Retry {}/{} due to 5xx server error. Waiting {} ms before retrying...",
                                retrySignal.totalRetries() + 1, RETRY_LIMIT, INITIAL_DELAY * (long) Math.pow(MULTIPLIER, retrySignal.totalRetries()))))
                .doOnError(e -> log.error("Failed to fetch resource: " + e.getMessage()))
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()));
    }

    private WebClient getWebClient(String organisation, String client, String location) {
        if (PwfUtils.isPwf(location)) {
            return WebClient.builder().build();
        }
        return WebClient.builder().build();
    }

    private RestTemplate getRestTemplate(String organisation, String client, String location) {
        if (PwfUtils.isPwf(location)) {
            return restTemplateProvider.getRestTemplate();
        }
        return restTemplateProvider.getAuthRestTemplate(organisation, client);
    }

}
