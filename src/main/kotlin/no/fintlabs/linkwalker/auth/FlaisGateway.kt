package no.fintlabs.linkwalker.auth

import kotlinx.coroutines.reactor.awaitSingle
import no.fintlabs.linkwalker.auth.AuthConstants.CLIENT_NAME
import no.fintlabs.linkwalker.auth.model.AuthObject
import no.fintlabs.linkwalker.auth.model.AuthResponse
import no.fintlabs.linkwalker.auth.model.ClientRequest
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class FlaisGateway(
    @Qualifier("flaisWebClient")
    private val webClient: WebClient
) {

    suspend fun getAuthObject(orgId: String): AuthObject? =
        getEncryptedAuthObject(orgId).takeIf { clientExists(it) }
            ?.let { decryptAuthResponse(it) }
            ?: decryptAuthResponse(createNewClient(ClientRequest(orgId = orgId)))

    private suspend fun getEncryptedAuthObject(orgName: String): AuthResponse =
        webClient.get()
            .uri(createUri(orgName))
            .retrieve()
            .bodyToMono(AuthResponse::class.java)
            .awaitSingle()

    private fun clientExists(authResponse: AuthResponse): Boolean = authResponse.authObject != null

    private suspend fun decryptAuthResponse(authResponse: AuthResponse): AuthObject =
        webClient.post()
            .uri("/client/decrypt")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(authResponse)
            .retrieve()
            .bodyToMono(AuthObject::class.java)
            .awaitSingle()

    private suspend fun createNewClient(clientRequest: ClientRequest): AuthResponse =
        webClient.post()
            .uri("/client")
            .bodyValue(clientRequest)
            .retrieve()
            .bodyToMono(AuthResponse::class.java)
            .awaitSingle()

    private fun createUri(orgId: String): String =
        orgId.replace('.', '_').replace("-", "_")
            .let { "/client/cn=${createCn(orgId)},ou=clients,ou=$it,ou=organisations,o=fint" }

    private fun createCn(orgId: String) =
        "$CLIENT_NAME@client.${orgId.replace("-", ".").replace("_", ".")}"

}
