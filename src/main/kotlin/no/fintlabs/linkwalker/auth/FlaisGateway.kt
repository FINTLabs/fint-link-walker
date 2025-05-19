package no.fintlabs.linkwalker.auth

import kotlinx.coroutines.reactor.awaitSingle
import no.fintlabs.linkwalker.auth.model.AuthObject
import no.fintlabs.linkwalker.auth.model.AuthResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class FlaisGateway(
    @Qualifier("flaisWebClient")
    private val webClient: WebClient
) {

    suspend fun getAuthObject(client: String, orgId: String): AuthObject? =
        determineFintType(client)?.let { username ->
            getEncryptedAuthObject(orgId, username)
                ?.let { resetPassword(it) }
                ?.let { decryptAuthResponse(it) }
        }

    fun determineFintType(username: String) =
        if (username.contains("@client")) username.lowercase()
        else null

    private suspend fun getEncryptedAuthObject(orgName: String, clientName: String): AuthResponse? =
        webClient.get()
            .uri(createUri(orgName, clientName))
            .retrieve()
            .bodyToMono(AuthResponse::class.java)
            .awaitSingle()

    private suspend fun resetPassword(authResponse: AuthResponse): AuthResponse =
        webClient.post()
            .uri("/client/password/reset")
            .bodyValue(authResponse)
            .retrieve()
            .bodyToMono(AuthResponse::class.java)
            .awaitSingle()

    private suspend fun decryptAuthResponse(authResponse: AuthResponse): AuthObject =
        webClient.post()
            .uri("/client/decrypt")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(authResponse)
            .retrieve()
            .bodyToMono(AuthObject::class.java)
            .awaitSingle()

    private fun createUri(orgName: String, clientName: String): String =
        orgName.replace('.', '_')
            .let { "/client/cn=$clientName,ou=clients,ou=$it,ou=organisations,o=fint" }

}
