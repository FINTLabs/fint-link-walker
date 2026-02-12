package no.fintlabs.linkwalker.auth

import kotlinx.coroutines.reactor.awaitSingle
import no.fintlabs.linkwalker.auth.AuthConstants.CLIENT_NAME
import no.fintlabs.linkwalker.auth.model.AuthObject
import no.fintlabs.linkwalker.auth.model.AuthResponse
import no.fintlabs.linkwalker.auth.model.ClientRequest
import no.fintlabs.linkwalker.config.LinkWalkerConfig
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class FlaisGateway(
    @Qualifier("flaisWebClient")
    private val webClient: WebClient,
    private val config: LinkWalkerConfig
) {

    /**
     * Retrieves authentication object for the given organization ID.
     * If the client exists, decrypts the encrypted auth response.
     * If the client does not exist, creates a new client and decrypts the response.
     */
    suspend fun getAuthObject(orgId: String): AuthObject? =
        getEncryptedAuthObject(orgId).takeIf { clientExists(it) }
            ?.let { decryptAuthResponse(it) }
            ?: decryptAuthResponse(createNewClient(ClientRequest(config.components, orgId)))

    private suspend fun getEncryptedAuthObject(orgName: String): AuthResponse =
        webClient.get()
            .uri(createUri(orgName))
            .retrieve()
            .bodyToMono<AuthResponse>()
            .awaitSingle()

    private fun clientExists(authResponse: AuthResponse): Boolean = authResponse.authObject != null

    private suspend fun decryptAuthResponse(authResponse: AuthResponse): AuthObject =
        webClient.post()
            .uri("/client/decrypt")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(authResponse)
            .retrieve()
            .bodyToMono<AuthObject>()
            .awaitSingle()

    private suspend fun createNewClient(clientRequest: ClientRequest): AuthResponse =
        webClient.post()
            .uri("/client")
            .bodyValue(clientRequest)
            .retrieve()
            .bodyToMono<AuthResponse>()
            .awaitSingle()

    private fun createUri(orgId: String): String =
        orgId.replace('.', '_').replace("-", "_")
            .let { "/client/cn=${createCn(orgId)},ou=clients,ou=$it,ou=organisations,o=fint" }

    private fun createCn(orgId: String) =
        "$CLIENT_NAME@client.${orgId.replace("-", ".").replace("_", ".")}"

}
