package no.novari.linkwalker.scanner

import kotlinx.coroutines.runBlocking
import no.novari.linkwalker.FetchRouting
import no.novari.linkwalker.FintClient
import no.novari.linkwalker.config.HttpProperties
import no.novari.linkwalker.config.ScannerProperties
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

class GatewayCanaryTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a clean page through the public host is reported as ok with the gateway node`() = runBlocking {
        server.enqueue(json("""{"ok":true}""").setHeader("Via", "1.1 beta (Access Gateway-ag-1)"))
        val canary = canary(fetchBase = "http://127.0.0.1:1")

        val result = canary.probe("t")!!

        assertEquals(true, result.ok)
        assertEquals("1.1 beta (Access Gateway-ag-1)", result.via)
        assertEquals("""{"ok":true}""".length.toLong(), result.bytes)
        assertNull(result.signature)
        assertEquals("/utdanning/elev/elevforhold?size=2000", server.takeRequest().path)
        assertEquals(publicBase() + "/utdanning/elev/elevforhold?size=2000", result.url)
    }

    @Test
    fun `a damaged page through the public host is reported with its signature and offset`() = runBlocking {
        val body = Buffer().write("""{"a":"x""".toByteArray()).write(byteArrayOf(0)).write(""""}""".toByteArray())
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body))
        val canary = canary(fetchBase = "http://127.0.0.1:1")

        val result = canary.probe("t")!!

        assertEquals(false, result.ok)
        assertEquals("nul-byte", result.signature)
        assertEquals(7L, result.byteOffset)
    }

    @Test
    fun `a failing fetch is reported and does not throw`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setHeader("Content-Type", "application/json").setBody("{}"))
        val canary = canary(fetchBase = "http://127.0.0.1:1")

        val result = canary.probe("t")!!

        assertEquals(false, result.ok)
        assertNotNull(result.error)
    }

    @Test
    fun `no canary runs when pages are fetched through the gateway anyway`() = runBlocking {
        val canary = canary(fetchBase = null)

        assertNull(canary.probe("t"))
        assertEquals(0, server.requestCount)
    }

    private fun publicBase(): String = server.url("/").toString().trimEnd('/')

    private fun canary(fetchBase: String?): GatewayCanary {
        val properties = ScannerProperties(orgId = "test_org", baseUrl = publicBase(), fetchBaseUrl = fetchBase)
        val routing = FetchRouting(properties)
        val client = FintClient(RestClient.builder().build(), HttpProperties(maxAttempts = 0), routing)
        return GatewayCanary(properties, client, routing)
    }

    private fun json(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
