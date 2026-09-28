package no.novari.linkwalker

import no.novari.linkwalker.config.ScannerProperties
import no.novari.linkwalker.config.ServiceRoutingProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import java.io.IOException
import java.net.ConnectException

class FetchRoutingTest {

    private val publicBase = "https://beta.example"

    @Test
    fun `a page in a client-api domain is fetched from the org's client-api Service first`() {
        val routing = routing(serviceEnabled = true)

        val first = routing.routes("$publicBase/utdanning/elev/elev?size=10").first()

        assertEquals("service", first.route)
        assertEquals("http://fint-core-client-api.mrfylke-no.svc.cluster.local:8080/utdanning/elev/elev?size=10", first.uri.toString())
        assertEquals(mapOf("x-org-id" to "mrfylke.no"), first.headers)
    }

    @Test
    fun `a page in a legacy domain is fetched from that component's consumer Service`() {
        val routing = routing(serviceEnabled = true)

        val first = routing.routes("$publicBase/okonomi/regnskap/leverandor?size=10").first()

        assertEquals("http://fint-core-consumer-okonomi-regnskap.mrfylke-no.svc.cluster.local:8080/okonomi/regnskap/leverandor?size=10", first.uri.toString())
    }

    @Test
    fun `the routes are the Service, then Traefik, then the public URL`() {
        val routing = routing(serviceEnabled = true, traefikBase = "http://traefik.flais-system")

        val routes = routing.routes("$publicBase/utdanning/elev/elev?size=10")

        assertEquals(listOf("service", "traefik", "gateway"), routes.map { it.route })
        assertEquals("http://traefik.flais-system/utdanning/elev/elev?size=10", routes[1].uri.toString())
        assertEquals("beta.example", routes[1].headers["Host"])
        assertEquals("$publicBase/utdanning/elev/elev?size=10", routes[2].uri.toString())
    }

    @Test
    fun `a page on another host only has the public route`() {
        val routing = routing(serviceEnabled = true, traefikBase = "http://traefik.flais-system")

        val routes = routing.routes("https://data.udir.no/kl06/x")

        assertEquals(listOf("gateway"), routes.map { it.route })
    }

    @Test
    fun `with nothing configured only the public route exists`() {
        val routes = routing(serviceEnabled = false).routes("$publicBase/utdanning/elev/elev?size=10")

        assertEquals(listOf("gateway"), routes.map { it.route })
    }

    @Test
    fun `a connection failure removes that Service for the rest of the scan but not the others`() {
        val routing = routing(serviceEnabled = true)
        val elevRoute = routing.routes("$publicBase/utdanning/elev/elev?size=10").first()

        assertTrue(routing.fallsThrough(elevRoute, connectionRefused()))

        assertEquals(listOf("gateway"), routing.routes("$publicBase/utdanning/vurdering/elevfravar?size=10").map { it.route })
        assertEquals(listOf("service", "gateway"), routing.routes("$publicBase/okonomi/regnskap/leverandor?size=10").map { it.route })
    }

    @Test
    fun `a 404 falls through without removing the Service`() {
        val routing = routing(serviceEnabled = true)
        val route = routing.routes("$publicBase/utdanning/elev/elev?size=10").first()

        assertTrue(routing.fallsThrough(route, HttpClientErrorException.create(HttpStatus.NOT_FOUND, "nf", HttpHeaders(), ByteArray(0), null)))

        assertEquals("service", routing.routes("$publicBase/utdanning/elev/elev?size=10").first().route)
    }

    @Test
    fun `a 500 or the public route never fall through`() {
        val routing = routing(serviceEnabled = true)
        val service = routing.routes("$publicBase/utdanning/elev/elev?size=10").first()
        val gateway = routing.routes("$publicBase/utdanning/elev/elev?size=10").last()

        assertFalse(routing.fallsThrough(service, HttpClientErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "x", HttpHeaders(), ByteArray(0), null)))
        assertFalse(routing.fallsThrough(gateway, connectionRefused()))
    }

    @Test
    fun `namespace and host pattern can be overridden`() {
        val routing = routing(
            serviceEnabled = true,
            service = ServiceRoutingProperties(enabled = true, hostPattern = "{service}.{namespace}:9090", namespace = "custom-ns"),
        )

        val first = routing.routes("$publicBase/utdanning/elev/elev").first()

        assertEquals("http://fint-core-client-api.custom-ns:9090/utdanning/elev/elev", first.uri.toString())
    }

    private fun routing(
        serviceEnabled: Boolean,
        traefikBase: String? = null,
        service: ServiceRoutingProperties = ServiceRoutingProperties(enabled = serviceEnabled),
    ): FetchRouting = FetchRouting(
        ScannerProperties(orgId = "mrfylke_no", baseUrl = publicBase, fetchBaseUrl = traefikBase, serviceRouting = service),
    )

    private fun connectionRefused() = ResourceAccessException("I/O error", IOException(ConnectException("Connection refused")))
}
