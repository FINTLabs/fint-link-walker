package no.novari.linkwalker.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.novari.linkwalker.auth.model.FintCredentials
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

    suspend fun getTokenResponse(credentials: FintCredentials): TokenResponse? =
        withContext(Dispatchers.IO) {
            restClient.post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(createFormData(credentials))
                .retrieve()
                .body<TokenResponse>()
        }

    private fun createFormData(credentials: FintCredentials): MultiValueMap<String, String> =
        LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "password")
            add("client_id", credentials.clientId)
            add("client_secret", credentials.clientSecret)
            add("username", credentials.username)
            add("password", credentials.password)
            add("scope", "fint-client")
        }
}
