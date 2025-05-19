package no.fintlabs.linkwalker.auth

import kotlinx.coroutines.reactive.awaitSingle
import no.fintlabs.linkwalker.auth.model.AuthObject
import no.fintlabs.linkwalker.auth.model.TokenResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient

@Component
class IdpClient(
    @Qualifier("authWebClient")
    private val webClient: WebClient
) {

    suspend fun getTokenResponse(authObject: AuthObject) =
        webClient.post()
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData(createFormData(authObject)))
            .retrieve()
            .bodyToMono(TokenResponse::class.java)
            .awaitSingle()

    private fun createFormData(authObject: AuthObject): MultiValueMap<String, String> =
        LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "password")
            add("client_id", authObject.clientId)
            add("client_secret", authObject.clientSecret)
            add("username", authObject.name)
            add("password", authObject.password)
            add("scope", "fint-client")
        }

}