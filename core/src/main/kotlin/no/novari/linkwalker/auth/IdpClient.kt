package no.novari.linkwalker.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.novari.linkwalker.auth.model.AuthObject
import no.novari.linkwalker.auth.model.TokenResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class IdpClient(
    @Qualifier("idpRestClient")
    private val restClient: RestClient,
) {

    suspend fun getTokenResponse(authObject: AuthObject): TokenResponse? =
        withContext(Dispatchers.IO) {
            restClient.post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(createFormData(authObject))
                .retrieve()
                .body<TokenResponse>()
        }

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
