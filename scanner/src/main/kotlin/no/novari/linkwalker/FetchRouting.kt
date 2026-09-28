package no.novari.linkwalker

import no.novari.linkwalker.config.ScannerProperties
import no.novari.linkwalker.config.ServiceRoutingProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.URI
import java.net.UnknownHostException
import java.net.http.HttpConnectTimeoutException
import java.nio.channels.UnresolvedAddressException
import java.util.concurrent.ConcurrentHashMap

/**
 * Decides where a page is fetched from.
 *
 * Pages are addressed by their public URL. Inside the cluster the same page can be fetched from
 * the org's own Kubernetes Service, or from Traefik with the public host in the `Host` header.
 * Both skip the Access Gateway that fronts the public host. [routes] lists the places to try, in
 * order, and the public URL itself is always the last one. Every request carries `x-org-id`,
 * which the gateway otherwise adds on the way in.
 */
@Component
class FetchRouting(properties: ScannerProperties) {

    private val publicBase: String = properties.baseUrl.trimEnd('/')
    private val traefikBase: String? = properties.fetchBaseUrl?.trimEnd('/')
    private val service: ServiceRoutingProperties = properties.serviceRouting
    private val namespace: String = service.namespace ?: properties.orgId.replace('_', '-')
    private val publicHost: String = URI(publicBase).let { if (it.port == -1) it.host else "${it.host}:${it.port}" }
    private val orgHeader: String = properties.orgId.replace('_', '.')
    private val clientApiDomains: Set<String> = service.clientApiDomains.map { it.lowercase() }.toSet()
    private val disabledHosts: MutableSet<String> = ConcurrentHashMap.newKeySet()

    val bypassConfigured: Boolean get() = service.enabled || traefikBase != null

    fun routes(url: String): List<RoutedRequest> {
        val inCluster = if (onPublicHost(url)) listOfNotNull(serviceRoute(url), traefikRoute(url)) else emptyList()
        return inCluster.filterNot { it.hostKey in disabledHosts } + direct(url)
    }

    fun direct(url: String): RoutedRequest =
        RoutedRequest(URI.create(url), mapOf(ORG_HEADER to orgHeader), route = GATEWAY, hostKey = GATEWAY)

    /**
     * Tells whether a failure on [request] means the next route should be tried. A connection
     * failure also takes that host out of the running for the rest of the scan, since every later
     * fetch from it would fail the same way. The public URL never falls through.
     */
    fun fallsThrough(request: RoutedRequest, failure: Throwable): Boolean {
        if (request.route == GATEWAY) return false
        if (failure is HttpClientErrorException && failure.statusCode.value() in FALLBACK_STATUSES) return true
        if (failure is ResourceAccessException && failure.isConnectionFailure()) {
            disabledHosts += request.hostKey
            return true
        }
        return false
    }

    private fun serviceRoute(url: String): RoutedRequest? {
        if (!service.enabled) return null
        val pathAndQuery = url.removePrefix(publicBase)
        val segments = pathAndQuery.substringBefore('?').split('/').filter { it.isNotEmpty() }
        if (segments.size < 2) return null
        val domain = segments[0]
        val pkg = segments[1]
        val serviceName =
            if (domain.lowercase() in clientApiDomains) {
                service.clientApiService
            } else {
                service.legacyServicePattern.replace("{domain}", domain.lowercase()).replace("{package}", pkg.lowercase())
            }
        val host = service.hostPattern.replace("{service}", serviceName).replace("{namespace}", namespace)
        return RoutedRequest(URI.create("http://$host$pathAndQuery"), mapOf(ORG_HEADER to orgHeader), route = SERVICE, hostKey = host)
    }

    private fun traefikRoute(url: String): RoutedRequest? =
        traefikBase?.let { base ->
            RoutedRequest(
                uri = URI.create(base + url.removePrefix(publicBase)),
                headers = mapOf("Host" to publicHost, ORG_HEADER to orgHeader),
                route = TRAEFIK,
                hostKey = base,
            )
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

    companion object {
        const val SERVICE = "service"
        const val TRAEFIK = "traefik"
        const val GATEWAY = "gateway"
        private const val ORG_HEADER = "x-org-id"
        private val FALLBACK_STATUSES = setOf(401, 403, 404)
    }
}

data class RoutedRequest(
    val uri: URI,
    val headers: Map<String, String>,
    val route: String,
    val hostKey: String,
)
