package no.novari.linkwalker.auth

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.novari.linkwalker.OrgId
import no.novari.linkwalker.auth.model.AuthObject
import no.novari.linkwalker.auth.model.FintCredentials
import no.novari.linkwalker.auth.model.TokenResponse
import no.novari.linkwalker.config.AuthProperties
import no.novari.linkwalker.config.CredentialsProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AuthServiceTest {

    private val flaisGateway = mockk<FlaisGateway>()
    private val idpClient = mockk<IdpClient>()

    private fun service(credentials: CredentialsProperties = CredentialsProperties()) =
        AuthService(flaisGateway, idpClient, AuthProperties(credentials = credentials))

    @Test
    fun `returns access token via Flais when no credentials configured`() = runBlocking {
        coEvery { flaisGateway.getAuthObject(OrgId("afk_no")) } returns fakeAuthObject()
        coEvery { idpClient.getTokenResponse(flaisCredentials()) } returns tokenResponse("ya29.bearer")

        assertEquals("ya29.bearer", service().getBearerToken(OrgId("afk_no")))
    }

    @Test
    fun `uses configured credentials and skips Flais entirely`() = runBlocking {
        val configured = CredentialsProperties(
            clientId = "cid", clientSecret = "csec", username = "user", password = "pw",
        )
        coEvery {
            idpClient.getTokenResponse(FintCredentials("cid", "csec", "user", "pw"))
        } returns tokenResponse("ya29.config")

        assertEquals("ya29.config", service(configured).getBearerToken(OrgId("afk_no")))
        coVerify(exactly = 0) { flaisGateway.getAuthObject(OrgId("afk_no")) }
    }

    @Test
    fun `returns null when FlaisGateway returns null`() = runBlocking {
        coEvery { flaisGateway.getAuthObject(OrgId("afk_no")) } returns null

        assertNull(service().getBearerToken(OrgId("afk_no")))
    }

    @Test
    fun `returns null when IdpClient returns null`() = runBlocking {
        coEvery { flaisGateway.getAuthObject(OrgId("afk_no")) } returns fakeAuthObject()
        coEvery { idpClient.getTokenResponse(flaisCredentials()) } returns null

        assertNull(service().getBearerToken(OrgId("afk_no")))
    }

    @Test
    fun `partially configured credentials fail fast`() {
        assertThrows(IllegalArgumentException::class.java) {
            CredentialsProperties(clientId = "cid")
        }
    }

    private fun fakeAuthObject() = AuthObject(
        dn = "cn=link-walker", name = "link-walker@client.afk.no",
        shortDescription = "", assetId = "", asset = "", note = "",
        password = "p", clientSecret = "s", publicKey = "", clientId = "c",
        components = emptyList(), accessPackages = emptyList(),
        managed = true,
    )

    private fun flaisCredentials() = FintCredentials(
        clientId = "c", clientSecret = "s", username = "link-walker@client.afk.no", password = "p",
    )

    private fun tokenResponse(token: String) = TokenResponse(
        accessToken = token, tokenType = "Bearer", expiresIn = 3600,
        acr = "default", scope = "fint-client",
    )
}
