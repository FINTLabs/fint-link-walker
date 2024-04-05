package no.fintlabs.linkwalker.request;

import lombok.RequiredArgsConstructor;
import no.fintlabs.linkwalker.request.model.Request;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Service
@RequiredArgsConstructor
public class RequestService {

    private final WebClient webClient;

    public Mono<Request> getRequest(String uri, String token) {
        return webClient.get()
                .uri(uri)
                .header(AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(Request.class);
    }

}
