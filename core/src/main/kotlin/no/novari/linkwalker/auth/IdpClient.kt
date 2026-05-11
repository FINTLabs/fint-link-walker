package no.novari.linkwalker.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.novari.linkwalker.auth.model.AuthObject
import no.novari.linkwalker.auth.model.TokenResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.body

@Component
class IdpClient(
    @Qualifier("idpRestClient")
    private val restClient: RestClient,
) {

    private val log = LoggerFactory.getLogger(IdpClient::class.java)

    suspend fun getTokenResponse(authObject: AuthObject): TokenResponse? =
        withContext(Dispatchers.IO) {
            log.info(
                "IDP POST token request: client_id={}, username={}, scope=fint-client, grant_type=password",
                authObject.clientId,
                authObject.name,
            )
            try {
                restClient.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(createFormData(authObject))
                    .retrieve()
                    .body<TokenResponse>()
                    ?.also { log.info("IDP POST token request -> token_type={}, expires_in={}", it.tokenType, it.expiresIn) }
            } catch (e: RestClientResponseException) {
                log.error(
                    "IDP POST token request failed: status={} client_id={} body={}",
                    e.statusCode,
                    authObject.clientId,
                    e.responseBodyAsString,
                )
                throw e
            }
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
