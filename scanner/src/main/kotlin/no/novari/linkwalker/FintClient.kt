package no.novari.linkwalker

import kotlinx.coroutines.delay
import no.novari.linkwalker.config.HttpProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.random.Random

class NoRouteException(message: String) : RuntimeException(message)
class NoDataException(message: String) : RuntimeException(message)

/** What one successful download returned, besides the body written to disk. */
data class FetchResult(
    val bytes: Long,
    val via: String?,
    val route: String,
)

@Component
class FintClient(
    private val fintRestClient: RestClient,
    private val httpProperties: HttpProperties,
    private val routing: FetchRouting,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Downloads the page at the public [url] into [destination], trying the routes from
     * [FetchRouting.routes] in order: the org's own Service, then Traefik, then the public URL.
     * A route that answers 401, 403 or 404, or cannot be connected to, hands over to the next one
     * with a warning. Failures on the public URL are thrown.
     */
    suspend fun streamToFile(url: String, bearer: String, destination: Path): FetchResult {
        val routes = routing.routes(url)
        routes.forEachIndexed { index, request ->
            try {
                return withRetry(url) { fetchAndCopy(request, bearer, destination) }
            } catch (ex: Exception) {
                val next = routes.getOrNull(index + 1)
                if (next == null || !routing.fallsThrough(request, ex)) throw ex
                logger.warn("Route {} failed for {} ({}), trying {} instead", request.route, url, ex.message, next.route)
            }
        }
        error("No route produced a result for $url")
    }

    /** Downloads [url] through the public host, whatever fetch base is configured. */
    suspend fun streamToFileThroughGateway(url: String, bearer: String, destination: Path): FetchResult =
        withRetry(url) { fetchAndCopy(routing.direct(url), bearer, destination) }

    private fun fetchAndCopy(request: RoutedRequest, bearer: String, destination: Path): FetchResult =
        fintRestClient.get()
            .uri(request.uri)
            .headers { headers ->
                headers.setBearerAuth(bearer)
                request.headers.forEach { (name, value) -> headers.set(name, value) }
            }
            .accept(MediaType.APPLICATION_JSON)
            .exchange { _, response ->
                val status = response.statusCode
                val contentType = response.headers.contentType

                when {
                    status.is2xxSuccessful && isJson(contentType) -> {
                        val bytes = Files.copy(
                            response.body,
                            destination,
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                        FetchResult(bytes = bytes, via = response.headers.getFirst("Via"), route = request.route)
                    }

                    status.is2xxSuccessful ->
                        throw NoRouteException(
                            "Non-JSON response (Content-Type=$contentType) from ${request.uri}"
                        )

                    status == HttpStatus.SERVICE_UNAVAILABLE -> {
                        val body = response.body.bufferedReader().use { it.readText() }
                        if (body.contains("CacheNotFoundException", ignoreCase = true)) {
                            throw NoDataException("CacheNotFoundException from ${request.uri} (no data in core)")
                        }
                        throw HttpClientErrorException.create(status, status.toString(), response.headers, body.toByteArray(), null)
                    }

                    else -> {
                        val body = response.body.bufferedReader().use { it.readText() }
                        logger.warn("FINT fetch error {} from {}: {}", status.value(), request.uri, body.take(300))
                        throw HttpClientErrorException.create(status, status.toString(), response.headers, body.toByteArray(), null)
                    }
                }
            }

    // Retry transient HTTP failures (I/O drops, 5xx) with exponential backoff + jitter.
    // 4xx and the typed NoRoute/NoData signals are caller-meaningful — they bypass retry.
    private suspend fun <T> withRetry(url: String, block: () -> T): T {
        val maxAttempts = httpProperties.maxAttempts
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (ex: NoRouteException) {
                throw ex
            } catch (ex: NoDataException) {
                throw ex
            } catch (ex: HttpClientErrorException) {
                if (ex.statusCode.is4xxClientError) throw ex
                attempt++
                if (attempt > maxAttempts) throw ex
                logger.warn("Retry #{} for {} – {}", attempt, url, ex.message)
                delay(backoffMs(attempt))
            } catch (ex: Exception) {
                attempt++
                if (attempt > maxAttempts) throw ex
                logger.warn("Retry #{} for {} – {}", attempt, url, ex.message)
                delay(backoffMs(attempt))
            }
        }
    }

    // 250ms × 2^(attempt-1), capped at 20s, plus ≤25% jitter.
    private fun backoffMs(attempt: Int): Long {
        val base = (250L shl (attempt - 1).coerceAtMost(6)).coerceAtMost(20_000L)
        val jitter = Random.nextLong((base * 0.25).toLong().coerceAtLeast(1))
        return base + jitter
    }

    private fun isJson(contentType: MediaType?): Boolean =
        contentType != null && contentType.subtype.contains("json", ignoreCase = true)
}
