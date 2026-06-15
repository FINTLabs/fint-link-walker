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

@Component
class FintClient(
    private val fintRestClient: RestClient,
    private val httpProperties: HttpProperties,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    // Suspend so callers can compose, but runs synchronously on the caller's dispatcher.
    // IndexBuilder dispatches through a parallelism-limited view of Dispatchers.IO;
    // a withContext(Dispatchers.IO) here would break that cap.
    suspend fun streamToFile(url: String, bearer: String, destination: Path) {
        withRetry(url) { fetchAndCopy(url, bearer, destination) }
    }

    private fun fetchAndCopy(url: String, bearer: String, destination: Path) {
        fintRestClient.get()
            .uri(url)
            .headers { it.setBearerAuth(bearer) }
            .accept(MediaType.APPLICATION_JSON)
            .exchange { _, response ->
                val status = response.statusCode
                val contentType = response.headers.contentType

                when {
                    status.is2xxSuccessful && isJson(contentType) -> {
                        Files.copy(
                            response.body,
                            destination,
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    }

                    status.is2xxSuccessful ->
                        throw NoRouteException(
                            "Non-JSON response (Content-Type=$contentType) from $url"
                        )

                    status == HttpStatus.SERVICE_UNAVAILABLE -> {
                        val body = response.body.bufferedReader().use { it.readText() }
                        if (body.contains("CacheNotFoundException", ignoreCase = true)) {
                            throw NoDataException("CacheNotFoundException from $url (no data in core)")
                        }
                        throw HttpClientErrorException.create(status, status.toString(), response.headers, body.toByteArray(), null)
                    }

                    else -> {
                        val body = response.body.bufferedReader().use { it.readText() }
                        throw HttpClientErrorException.create(status, status.toString(), response.headers, body.toByteArray(), null)
                    }
                }
            }
    }

    // Retry transient HTTP failures (I/O drops, 5xx) with exponential backoff + jitter.
    // 4xx and the typed NoRoute/NoData signals are caller-meaningful — they bypass retry.
    private suspend fun withRetry(url: String, block: () -> Unit) {
        val maxAttempts = httpProperties.maxAttempts
        var attempt = 0
        while (true) {
            try {
                block()
                return
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
