package no.novari.linkwalker.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.novari.linkwalker.auth.AuthConstants.CLIENT_NAME
import no.novari.linkwalker.OrgId
import no.novari.linkwalker.auth.model.AuthObject
import no.novari.linkwalker.auth.model.AuthResponse
import no.novari.linkwalker.auth.model.ClientData
import no.novari.linkwalker.auth.model.ClientRequest
import no.novari.linkwalker.config.ScannerProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.body

@Component
class FlaisGateway(
    @Qualifier("flaisRestClient")
    private val restClient: RestClient,
    private val config: ScannerProperties,
) {

    private val log = LoggerFactory.getLogger(FlaisGateway::class.java)

    suspend fun getAuthObject(orgId: OrgId): AuthObject? = withContext(Dispatchers.IO) {
        val raw = orgId.value
        val encrypted = getEncryptedAuthObject(raw)
        if (clientExists(encrypted)) {
            decryptAuthResponse(encrypted)
        } else {
            decryptAuthResponse(createNewClient(raw))
        }
    }

    private fun getEncryptedAuthObject(orgId: String): AuthResponse {
        val uri = createUri(orgId)
        log.info("Flais GET {}", uri)
        return logFailure("Flais GET $uri") {
            restClient.get()
                .uri(uri)
                .retrieve()
                .body<AuthResponse>()
                ?: error("Empty response from flais-gateway for $orgId")
        }.also { log.info("Flais GET {} -> operation={}, clientExists={}", uri, it.operation, it.authObject != null) }
    }

    private fun clientExists(authResponse: AuthResponse): Boolean = authResponse.authObject != null

    private fun decryptAuthResponse(authResponse: AuthResponse): AuthObject {
        log.info("Flais POST /client/decrypt for orgId={}", authResponse.orgId)
        return logFailure("Flais POST /client/decrypt") {
            restClient.post()
                .uri("/client/decrypt")
                .contentType(MediaType.APPLICATION_JSON)
                .body(authResponse)
                .retrieve()
                .body<AuthObject>()
                ?: error("Empty response from flais-gateway decrypt")
        }.also { log.info("Flais POST /client/decrypt -> clientId={}, name={}", it.clientId, it.name) }
    }

    private fun createNewClient(orgId: String): AuthResponse {
        val request = ClientRequest(orgId = dotted(orgId), clientData = ClientData.forComponents(config.components))
        log.info("Flais POST /client body={}", request)
        return logFailure("Flais POST /client") {
            restClient.post()
                .uri("/client")
                .body(request)
                .retrieve()
                .body<AuthResponse>()
                ?: error("Empty response from flais-gateway client creation")
        }.also { log.info("Flais POST /client -> operation={}, clientExists={}", it.operation, it.authObject != null) }
    }

    private inline fun <T> logFailure(label: String, block: () -> T): T = try {
        block()
    } catch (e: RestClientResponseException) {
        log.error("{} failed: status={} body={}", label, e.statusCode, e.responseBodyAsString)
        throw e
    }

    // OU uses the underscore form (already what OrgId enforces); CN uses dotted DNS-style.
    private fun createUri(orgId: String): String =
        "/client/cn=${cnFor(orgId)},ou=clients,ou=$orgId,ou=organisations,o=fint"

    private fun cnFor(orgId: String): String = "$CLIENT_NAME@client.${dotted(orgId)}"

    private fun dotted(orgId: String): String = orgId.replace('_', '.')
}
