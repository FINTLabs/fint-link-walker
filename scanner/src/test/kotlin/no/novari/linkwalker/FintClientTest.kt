package no.novari.linkwalker

import kotlinx.coroutines.runBlocking
import no.novari.linkwalker.config.HttpProperties
import no.novari.linkwalker.config.ScannerProperties
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class FintClientTest {

    private lateinit var server: MockWebServer
    private lateinit var restClient: RestClient
    private lateinit var client: FintClient

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        // Short read timeout so DISCONNECT_* socket policies surface as
        // ResourceAccessException quickly instead of hanging on the JDK
        // HttpClient's default (no) read timeout.
        val requestFactory = JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
        ).apply { setReadTimeout(Duration.ofSeconds(1)) }
        restClient = RestClient.builder().requestFactory(requestFactory).build()
        client = FintClient(
            fintRestClient = restClient,
            httpProperties = HttpProperties(maxAttempts = 2),
            routing = FetchRouting(ScannerProperties(orgId = "test_org")),
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `200 + JSON streams body to destination`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"hello":"world"}"""),
        )
        val dest = tempDir.resolve("out.json")

        client.streamToFile(server.url("/data").toString(), "bearer-x", dest)

        assertEquals("""{"hello":"world"}""", Files.readString(dest))
        val recorded = server.takeRequest()
        assertEquals("Bearer bearer-x", recorded.getHeader("Authorization"))
        assertEquals("test.org", recorded.getHeader("x-org-id"))
    }

    @Test
    fun `200 + vendor JSON content-type still streams`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/vnd.fint.no.v1+json")
                .setBody("""{"k":1}"""),
        )
        val dest = tempDir.resolve("out.json")

        client.streamToFile(server.url("/data").toString(), "t", dest)

        assertEquals("""{"k":1}""", Files.readString(dest))
    }

    @Test
    fun `200 + HTML throws NoRouteException`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html")
                .setBody("<html>not deployed</html>"),
        )

        assertThrows(NoRouteException::class.java) {
            runBlocking {
                client.streamToFile(server.url("/utdanning/ot").toString(), "t", tempDir.resolve("x"))
            }
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `503 + CacheNotFoundException body throws NoDataException`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"CacheNotFoundException: no data"}"""),
        )

        assertThrows(NoDataException::class.java) {
            runBlocking {
                client.streamToFile(server.url("/data").toString(), "t", tempDir.resolve("x"))
            }
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `5xx retries up to maxAttempts then fails`() {
        // maxAttempts = 2 → one initial + two retries = three total requests
        repeat(3) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(503)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"error":"transient"}"""),
            )
        }

        assertThrows(HttpClientErrorException::class.java) {
            runBlocking {
                client.streamToFile(server.url("/data").toString(), "t", tempDir.resolve("x"))
            }
        }
        assertEquals(3, server.requestCount, "should retry transient 5xx until budget exhausted")
    }

    @Test
    fun `5xx recovers if a retry succeeds`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"transient"}"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"ok":true}"""),
        )
        val dest = tempDir.resolve("out.json")

        client.streamToFile(server.url("/data").toString(), "t", dest)

        assertEquals("""{"ok":true}""", Files.readString(dest))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `connection drop mid-response retries and recovers`() = runBlocking {
        // Reproduces the PrematureClose incident: FINT API closes the chunked
        // body mid-stream → ResourceAccessException → retry → success.
        server.enqueue(disconnectDuringBody())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"ok":true}"""),
        )
        val dest = tempDir.resolve("out.json")

        client.streamToFile(server.url("/data").toString(), "t", dest)

        assertEquals("""{"ok":true}""", Files.readString(dest))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `connection drop exhausts retry budget`() {
        // maxAttempts = 2 → one initial + two retries = three total requests
        repeat(3) { server.enqueue(disconnectDuringBody()) }

        assertThrows(ResourceAccessException::class.java) {
            runBlocking {
                client.streamToFile(server.url("/data").toString(), "t", tempDir.resolve("x"))
            }
        }
        assertEquals(3, server.requestCount)
    }


    @Test
    fun `a page on the public host is fetched from the fetch base with the public host in the Host header`() = runBlocking {
        server.enqueue(jsonResponse("""{"page":1}""").setHeader("Via", "1.1 beta.example (Access Gateway-ag-1)"))
        val bypassing = clientWith(publicBase = "https://beta.example", fetchBase = server.url("/").toString().trimEnd('/'))
        val dest = tempDir.resolve("out.json")

        val result = bypassing.streamToFile("https://beta.example/utdanning/elev/elev?size=10", "t", dest)

        val recorded = server.takeRequest()
        assertEquals("/utdanning/elev/elev?size=10", recorded.path)
        assertEquals("beta.example", recorded.getHeader("Host"))
        assertEquals("test.org", recorded.getHeader("x-org-id"))
        assertEquals("Bearer t", recorded.getHeader("Authorization"))
        assertEquals("""{"page":1}""", Files.readString(dest))
        assertEquals("1.1 beta.example (Access Gateway-ag-1)", result.via)
        assertEquals("""{"page":1}""".length.toLong(), result.bytes)
    }

    @Test
    fun `a 404 from the fetch base is answered from the public URL`() = runBlocking {
        val internal = MockWebServer().apply { start() }
        try {
            internal.enqueue(MockResponse().setResponseCode(404).setHeader("Content-Type", "text/plain").setBody("404 page not found"))
            server.enqueue(jsonResponse("""{"from":"public"}"""))
            val publicBase = server.url("/").toString().trimEnd('/')
            val bypassing = clientWith(publicBase = publicBase, fetchBase = internal.url("/").toString().trimEnd('/'))
            val dest = tempDir.resolve("out.json")

            bypassing.streamToFile("$publicBase/utdanning/elev/elev?size=10", "t", dest)

            assertEquals(1, internal.requestCount)
            assertEquals(1, server.requestCount)
            assertEquals("/utdanning/elev/elev?size=10", server.takeRequest().path)
            assertEquals("""{"from":"public"}""", Files.readString(dest))
        } finally {
            internal.shutdown()
        }
    }

    @Test
    fun `a connection failure on the fetch base turns the bypass off for the rest of the scan`() = runBlocking {
        server.enqueue(jsonResponse("""{"n":1}"""))
        server.enqueue(jsonResponse("""{"n":2}"""))
        val publicBase = server.url("/").toString().trimEnd('/')
        val routing = FetchRouting(ScannerProperties(orgId = "test_org", baseUrl = publicBase, fetchBaseUrl = "http://127.0.0.1:1"))
        val bypassing = FintClient(restClient, HttpProperties(maxAttempts = 0), routing)
        val url = "$publicBase/utdanning/elev/elev?size=10"

        bypassing.streamToFile(url, "t", tempDir.resolve("one.json"))
        bypassing.streamToFile(url, "t", tempDir.resolve("two.json"))

        assertEquals(2, server.requestCount)
        assertEquals(false, routing.route(url).bypassesGateway)
        assertEquals("""{"n":2}""", Files.readString(tempDir.resolve("two.json")))
    }

    @Test
    fun `a fetch through the gateway ignores the fetch base`() = runBlocking {
        val internal = MockWebServer().apply { start() }
        try {
            server.enqueue(jsonResponse("""{"canary":true}"""))
            val publicBase = server.url("/").toString().trimEnd('/')
            val bypassing = clientWith(publicBase = publicBase, fetchBase = internal.url("/").toString().trimEnd('/'))

            bypassing.streamToFileThroughGateway("$publicBase/utdanning/elev/elevforhold?size=2000", "t", tempDir.resolve("c.json"))

            assertEquals(0, internal.requestCount)
            assertEquals(1, server.requestCount)
        } finally {
            internal.shutdown()
        }
    }

    private fun clientWith(publicBase: String, fetchBase: String): FintClient = FintClient(
        fintRestClient = restClient,
        httpProperties = HttpProperties(maxAttempts = 1),
        routing = FetchRouting(ScannerProperties(orgId = "test_org", baseUrl = publicBase, fetchBaseUrl = fetchBase)),
    )

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun disconnectDuringBody(): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"partial":""")
        .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)

    @Test
    fun `4xx fails immediately`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"not found"}"""),
        )

        val ex = assertThrows(HttpClientErrorException::class.java) {
            runBlocking {
                client.streamToFile(server.url("/missing").toString(), "t", tempDir.resolve("x"))
            }
        }
        assertTrue(ex.statusCode.is4xxClientError)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `streamToFile overwrites existing destination`() = runBlocking {
        val dest = tempDir.resolve("out.json")
        Files.writeString(dest, "stale-data")

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"fresh":true}"""),
        )

        client.streamToFile(server.url("/data").toString(), "t", dest)

        assertEquals("""{"fresh":true}""", Files.readString(dest))
    }
}
