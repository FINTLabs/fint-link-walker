package no.novari.linkwalker.auth

import kotlinx.coroutines.runBlocking
import no.novari.linkwalker.auth.model.AuthObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import java.net.URLDecoder

class IdpClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: IdpClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = IdpClient(
            restClient = RestClient.builder().baseUrl(server.url("/").toString()).build(),
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `posts form-encoded credentials and parses access token`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "access_token": "ya29.eyJabc",
                      "token_type": "Bearer",
                      "expires_in": 3600,
                      "acr": "default",
                      "scope": "fint-client"
                    }
                    """.trimIndent(),
                ),
        )

        val token = client.getTokenResponse(authObject(name = "user@client", clientId = "cid", clientSecret = "csec", password = "pw"))

        assertEquals("ya29.eyJabc", token?.accessToken)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("application/x-www-form-urlencoded", recorded.getHeader("Content-Type")?.substringBefore(";"))

        val form = recorded.body.readUtf8()
            .split("&")
            .associate { kv ->
                val (k, v) = kv.split("=", limit = 2)
                URLDecoder.decode(k, Charsets.UTF_8) to URLDecoder.decode(v, Charsets.UTF_8)
            }
        assertEquals("password", form["grant_type"])
        assertEquals("cid", form["client_id"])
        assertEquals("csec", form["client_secret"])
        assertEquals("user@client", form["username"])
        assertEquals("pw", form["password"])
        assertEquals("fint-client", form["scope"])
    }

    @Test
    fun `propagates 4xx as exception`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":"invalid_grant"}"""),
        )

        val ex = runCatching { runBlocking { client.getTokenResponse(authObject()) } }.exceptionOrNull()
        assertTrue(ex != null, "Expected an exception on 401")
    }

    private fun authObject(
        name: String = "n",
        clientId: String = "c",
        clientSecret: String = "s",
        password: String = "p",
    ) = AuthObject(
        dn = "", name = name, shortDescription = "", assetId = "", asset = "", note = "",
        password = password, clientSecret = clientSecret, publicKey = "", clientId = clientId,
        components = mutableListOf(), accessPackages = mutableListOf(), managed = true,
    )
}
