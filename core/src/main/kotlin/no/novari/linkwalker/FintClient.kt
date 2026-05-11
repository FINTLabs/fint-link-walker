package no.novari.linkwalker

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class NoRouteException(message: String) : RuntimeException(message)
class NoDataException(message: String) : RuntimeException(message)

@Component
class FintClient(
    private val fintRestClient: RestClient,
) {

    // Suspend so callers can compose, but runs synchronously on the caller's dispatcher.
    // IndexBuilder dispatches through a parallelism-limited view of Dispatchers.IO;
    // a withContext(Dispatchers.IO) here would break that cap.
    suspend fun streamToFile(url: String, bearer: String, destination: Path) {
        fetchAndCopy(url, bearer, destination)
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

    private fun isJson(contentType: MediaType?): Boolean =
        contentType != null && contentType.subtype.contains("json", ignoreCase = true)
}
