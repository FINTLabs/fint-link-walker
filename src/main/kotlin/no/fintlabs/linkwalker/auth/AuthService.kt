package no.fintlabs.linkwalker.auth

import org.springframework.stereotype.Service

@Service
class AuthService(
    private val flaisGateway: FlaisGateway,
    private val idpClient: IdpClient
) {

    suspend fun getBearerToken(authHeader: String?, client: String?, orgId: String): String? =
        authHeader?.removePrefix("Bearer ")
            ?: client?.let { getBearerToken(orgId, it) }

    private suspend fun getBearerToken(client: String, orgId: String): String? =
        flaisGateway.getAuthObject(orgId, client)
            ?.let { idpClient.getTokenResponse(it) }
            ?.accessToken

}