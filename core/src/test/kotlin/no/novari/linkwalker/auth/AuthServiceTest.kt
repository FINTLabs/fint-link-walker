package no.novari.linkwalker.auth

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.novari.linkwalker.auth.model.AuthObject
import no.novari.linkwalker.auth.model.TokenResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AuthServiceTest {

    private val flaisGateway = mockk<FlaisGateway>()
    private val idpClient = mockk<IdpClient>()
    private val service = AuthService(flaisGateway, idpClient)

    @Test
    fun `returns access token on happy path`() = runBlocking {
        val authObject = fakeAuthObject()
        coEvery { flaisGateway.getAuthObject("afk-no") } returns authObject
        coEvery { idpClient.getTokenResponse(authObject) } returns tokenResponse("ya29.bearer")

        assertEquals("ya29.bearer", service.getBearerToken("afk-no"))
    }

    @Test
    fun `returns null when FlaisGateway returns null`() = runBlocking {
        coEvery { flaisGateway.getAuthObject("afk-no") } returns null

        assertNull(service.getBearerToken("afk-no"))
    }

    @Test
    fun `returns null when IdpClient returns null`() = runBlocking {
        val authObject = fakeAuthObject()
        coEvery { flaisGateway.getAuthObject("afk-no") } returns authObject
        coEvery { idpClient.getTokenResponse(authObject) } returns null

        assertNull(service.getBearerToken("afk-no"))
    }

    private fun fakeAuthObject() = AuthObject(
        dn = "cn=link-walker", name = "link-walker@client.afk.no",
        shortDescription = "", assetId = "", asset = "", note = "",
        password = "p", clientSecret = "s", publicKey = "", clientId = "c",
        components = mutableListOf(), accessPackages = mutableListOf(),
        managed = true,
    )

    private fun tokenResponse(token: String) = TokenResponse(
        accessToken = token, tokenType = "Bearer", expiresIn = 3600,
        acr = "default", scope = "fint-client",
    )
}
