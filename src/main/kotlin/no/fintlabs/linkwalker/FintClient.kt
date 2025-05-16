package no.fintlabs.linkwalker

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.util.retry.Retry
import java.io.IOException
import java.time.Duration

@Component
class FintClient(
    private val webClient: WebClient
) {

    private companion object {
        private val RETRY_SPEC = Retry
            .backoff(3, Duration.ofMillis(250))
            .filter { t ->
                t is IOException ||
                        (t is WebClientResponseException && t.statusCode.is5xxServerError)
            }
    }

    suspend fun getHateosResources(url: String, bearer: String): JsonNode =
        webClient.get()
            .uri(url)
            .headers { h -> h.setBearerAuth(bearer) }
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .bodyToMono(JsonNode::class.java)
            .retryWhen(RETRY_SPEC)
            .awaitSingle()

    suspend fun getEmbeddedResources(url: String, bearer: String) =
        getHateosResources(url, bearer).path("_embedded").path("_entries").toList()

}