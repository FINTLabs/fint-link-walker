package no.novari.linkwalker.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.novari.linkwalker.auth.AuthConstants.CLIENT_NAME
import no.novari.linkwalker.auth.model.AuthObject
import no.novari.linkwalker.auth.model.AuthResponse
import no.novari.linkwalker.auth.model.ClientRequest
import no.novari.linkwalker.config.LinkWalkerConfig
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class FlaisGateway(
    @Qualifier("flaisRestClient")
    private val restClient: RestClient,
    private val config: LinkWalkerConfig,
) {

    suspend fun getAuthObject(orgId: String): AuthObject? = withContext(Dispatchers.IO) {
        val encrypted = getEncryptedAuthObject(orgId)
        if (clientExists(encrypted)) {
            decryptAuthResponse(encrypted)
        } else {
            decryptAuthResponse(createNewClient(ClientRequest(config.components, orgId)))
        }
    }

    private fun getEncryptedAuthObject(orgName: String): AuthResponse =
        restClient.get()
            .uri(createUri(orgName))
            .retrieve()
            .body<AuthResponse>()
            ?: error("Empty response from flais-gateway for $orgName")

    private fun clientExists(authResponse: AuthResponse): Boolean = authResponse.authObject != null

    private fun decryptAuthResponse(authResponse: AuthResponse): AuthObject =
        restClient.post()
            .uri("/client/decrypt")
            .contentType(MediaType.APPLICATION_JSON)
            .body(authResponse)
            .retrieve()
            .body<AuthObject>()
            ?: error("Empty response from flais-gateway decrypt")

    private fun createNewClient(clientRequest: ClientRequest): AuthResponse =
        restClient.post()
            .uri("/client")
            .body(clientRequest)
            .retrieve()
            .body<AuthResponse>()
            ?: error("Empty response from flais-gateway client creation")

    private fun createUri(orgId: String): String =
        orgId.replace('.', '_').replace("-", "_")
            .let { "/client/cn=${createCn(orgId)},ou=clients,ou=$it,ou=organisations,o=fint" }

    private fun createCn(orgId: String) =
        "$CLIENT_NAME@client.${orgId.replace("-", ".").replace("_", ".")}"
}
