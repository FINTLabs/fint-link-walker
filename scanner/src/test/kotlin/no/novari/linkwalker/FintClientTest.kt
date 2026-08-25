package no.novari.linkwalker

import kotlinx.coroutines.runBlocking
import no.novari.linkwalker.config.HttpProperties
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
        client = FintClient(
            fintRestClient = RestClient.builder().requestFactory(requestFactory).build(),
            httpProperties = HttpProperties(maxAttempts = 2),
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
