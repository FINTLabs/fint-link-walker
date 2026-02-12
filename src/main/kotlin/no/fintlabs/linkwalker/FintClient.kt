package no.fintlabs.linkwalker

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.reactor.awaitSingle
import no.fintlabs.linkwalker.config.LinkWalkerConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.retry.Retry
import java.time.Duration

@Component
class FintClient(
    private val webClient: WebClient,
    config: LinkWalkerConfig,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val retrySpec =
        Retry
            .backoff(config.maxAttempts, Duration.ofMillis(250)) // how many times
            .maxBackoff(Duration.ofSeconds(20))
            .jitter(0.25)
            .doBeforeRetry { sig ->
                logger.warn("Retry #{} – {}", sig.totalRetries() + 1, sig.failure().message)
            }

    suspend fun getHateosResources(
        url: String,
        bearer: String,
    ): JsonNode =
        webClient
            .get()
            .uri(url)
            .headers { h -> h.setBearerAuth(bearer) }
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .bodyToMono(JsonNode::class.java)
            .retryWhen(retrySpec)
            .awaitSingle()

    suspend fun getEmbeddedResources(
        url: String,
        bearer: String,
    ) = getHateosResources(url, bearer).path("_embedded").path("_entries").toList()
}
