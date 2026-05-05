package no.novari.linkwalker.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import no.novari.linkwalker.auth.model.AuthObject
import no.novari.linkwalker.auth.model.AuthResponse
import no.novari.linkwalker.config.LinkWalkerConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

class FlaisGatewayTest {

    private val mapper: ObjectMapper = jacksonObjectMapper().findAndRegisterModules()

    private lateinit var server: MockWebServer
    private lateinit var gateway: FlaisGateway

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        gateway = FlaisGateway(
            restClient = RestClient.builder().baseUrl(server.url("/").toString()).build(),
            config = LinkWalkerConfig(components = listOf("utdanning_elev")),
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `existing client is decrypted without creating new one`() = runBlocking {
        // GET /client/cn=...  → existing AuthResponse (object != null)
        server.enqueue(jsonResponse(authResponseWithObject()))
        // POST /client/decrypt → AuthObject
        server.enqueue(jsonResponse(decryptedAuthObject()))

        val result = gateway.getAuthObject("afk-no")

        assertNotNull(result)
        assertEquals("link-walker@client.afk.no", result!!.name)

        val getRequest = server.takeRequest()
        assertEquals("GET", getRequest.method)
        assertEquals(
            "/client/cn=link-walker@client.afk.no,ou=clients,ou=afk_no,ou=organisations,o=fint",
            getRequest.path,
        )

        val decryptRequest = server.takeRequest()
        assertEquals("POST", decryptRequest.method)
        assertEquals("/client/decrypt", decryptRequest.path)

        assertEquals(2, server.requestCount, "Existing client should not trigger create")
    }

    @Test
    fun `missing client triggers create then decrypt`() = runBlocking {
        // GET /client/cn=...  → AuthResponse with null object
        server.enqueue(jsonResponse(authResponseWithoutObject()))
        // POST /client → newly-created AuthResponse with object
        server.enqueue(jsonResponse(authResponseWithObject()))
        // POST /client/decrypt → AuthObject
        server.enqueue(jsonResponse(decryptedAuthObject()))

        val result = gateway.getAuthObject("afk-no")

        assertNotNull(result)

        server.takeRequest() // GET lookup
        val createRequest = server.takeRequest()
        assertEquals("POST", createRequest.method)
        assertEquals("/client", createRequest.path)
        // verify dotted orgId in body
        val body = createRequest.body.readUtf8()
        assert(body.contains("\"orgId\":\"afk.no\""))
        assert(body.contains("ou=utdanning_elev,ou=components,o=fint"))

        val decryptRequest = server.takeRequest()
        assertEquals("/client/decrypt", decryptRequest.path)
    }

    @Test
    fun `URI encodes underscored OU and dotted CN`() = runBlocking {
        server.enqueue(jsonResponse(authResponseWithObject()))
        server.enqueue(jsonResponse(decryptedAuthObject()))

        gateway.getAuthObject("agderfk-no")

        val getRequest = server.takeRequest()
        assertEquals(
            "/client/cn=link-walker@client.agderfk.no,ou=clients,ou=agderfk_no,ou=organisations,o=fint",
            getRequest.path,
        )
    }

    @Test
    fun `throws on empty lookup response`() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json"))

        assertThrows(IllegalStateException::class.java) {
            runBlocking { gateway.getAuthObject("afk-no") }
        }
    }

    @Test
    fun `4xx from FLAIS propagates as exception`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))

        val ex = runCatching { runBlocking { gateway.getAuthObject("afk-no") } }.exceptionOrNull()
        assert(ex != null)
    }

    private fun authResponseWithObject() = AuthResponse(
        authObject = decryptedAuthObject(),
        orgId = "afk.no",
        operation = "READ",
        errorMessage = null,
    )

    private fun authResponseWithoutObject() = AuthResponse(
        authObject = null,
        orgId = "afk.no",
        operation = "CREATE_REQUIRED",
        errorMessage = null,
    )

    private fun decryptedAuthObject() = AuthObject(
        dn = "cn=link-walker@client.afk.no,ou=clients,ou=afk_no,ou=organisations,o=fint",
        name = "link-walker@client.afk.no",
        shortDescription = "Autogenerert relasjontester",
        assetId = "", asset = "", note = "",
        password = "p", clientSecret = "s", publicKey = "", clientId = "c",
        components = mutableListOf("ou=utdanning_elev,ou=components,o=fint"),
        accessPackages = mutableListOf(),
        managed = true,
    )

    private fun jsonResponse(body: Any): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(mapper.writeValueAsString(body))
}
