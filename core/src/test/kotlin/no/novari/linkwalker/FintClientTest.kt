package no.novari.linkwalker

import kotlinx.coroutines.runBlocking
import no.novari.linkwalker.config.LinkWalkerConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.nio.file.Files
import java.nio.file.Path

class FintClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: FintClient

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = FintClient(
            fintRestClient = RestClient.builder().build(),
            config = LinkWalkerConfig(maxAttempts = 2),
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
    fun `200 + HTML throws NoRouteException without retry`() {
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
        assertEquals(1, server.requestCount, "NoRoute must not retry")
    }

    @Test
    fun `503 + CacheNotFoundException body throws NoDataException without retry`() {
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
        assertEquals(1, server.requestCount, "NoData must not retry")
    }

    @Test
    fun `503 without CacheNotFoundException is retried as 5xx`() {
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
        assertEquals(3, server.requestCount, "1 initial + 2 retries with maxAttempts=2")
    }

    @Test
    fun `4xx is not retried`() {
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
        assertEquals(1, server.requestCount, "4xx must not retry")
    }

    @Test
    fun `5xx then 200 succeeds after retry`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
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
