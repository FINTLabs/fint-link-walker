package no.novari.linkwalker

import no.novari.linkwalker.config.ScannerProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.URI
import java.net.UnknownHostException
import java.net.http.HttpConnectTimeoutException
import java.nio.channels.UnresolvedAddressException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Decides where a page is fetched from.
 *
 * Pages are addressed by their public URL. When `fetch-base-url` is set, a URL on the public host
 * is fetched from that base instead, with the public host in the `Host` header, so the request
 * reaches the same Traefik route without passing the Access Gateway that fronts the public host.
 * Every request carries `x-org-id`, which the gateway otherwise adds on the way in.
 */
@Component
class FetchRouting(properties: ScannerProperties) {

    private val publicBase: String = properties.baseUrl.trimEnd('/')
    private val fetchBase: String? = properties.fetchBaseUrl?.trimEnd('/')
    private val publicHost: String = URI(publicBase).let { if (it.port == -1) it.host else "${it.host}:${it.port}" }
    private val orgHeader: String = properties.orgId.replace('_', '.')
    private val bypassDisabled = AtomicBoolean(false)

    val bypassConfigured: Boolean get() = fetchBase != null

    fun route(url: String): RoutedRequest {
        val base = fetchBase
        if (base != null && !bypassDisabled.get() && onPublicHost(url)) {
            return RoutedRequest(
                uri = URI.create(base + url.removePrefix(publicBase)),
                headers = mapOf("Host" to publicHost, "x-org-id" to orgHeader),
                bypassesGateway = true,
            )
        }
        return direct(url)
    }

    fun direct(url: String): RoutedRequest =
        RoutedRequest(URI.create(url), mapOf("x-org-id" to orgHeader), bypassesGateway = false)

    /**
     * Tells whether a failure on the bypass route should be answered by fetching the public URL
     * instead. A connection failure also turns the bypass off for the rest of the scan, since
     * every later fetch would fail the same way.
     */
    fun fallsBackToGateway(failure: Throwable): Boolean {
        if (failure is HttpClientErrorException && failure.statusCode.value() in FALLBACK_STATUSES) return true
        if (failure is ResourceAccessException && failure.isConnectionFailure()) {
            bypassDisabled.set(true)
            return true
        }
        return false
    }

    private fun onPublicHost(url: String): Boolean = url == publicBase || url.startsWith("$publicBase/")

    private fun Throwable.isConnectionFailure(): Boolean =
        generateSequence(this) { it.cause }.any {
            it is ConnectException ||
                it is UnknownHostException ||
                it is NoRouteToHostException ||
                it is HttpConnectTimeoutException ||
                it is UnresolvedAddressException
        }

    private companion object {
        val FALLBACK_STATUSES = setOf(401, 403, 404)
    }
}

data class RoutedRequest(
    val uri: URI,
    val headers: Map<String, String>,
    val bypassesGateway: Boolean,
)
