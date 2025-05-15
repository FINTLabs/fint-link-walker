package no.fintlabs.linkwalker

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class FintClient(
    private val webClient: WebClient
) {

    suspend fun getHateosResources(url: String, bearer: String): JsonNode =
        webClient
            .get()
            .uri(url)
            .headers { h -> h.setBearerAuth(bearer) }
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .bodyToMono(JsonNode::class.java)
            .awaitSingle()

    suspend fun getEmbeddedResources(url: String, bearer: String) =
        getHateosResources(url, bearer).path("_embedded").path("_entries").toList()

}