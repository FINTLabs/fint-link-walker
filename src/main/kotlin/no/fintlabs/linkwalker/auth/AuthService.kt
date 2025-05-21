package no.fintlabs.linkwalker.auth

import no.fintlabs.linkwalker.task.model.TaskRequest
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val flaisGateway: FlaisGateway,
    private val idpClient: IdpClient
) {

    suspend fun getBearerToken(orgId: String, taskRequest: TaskRequest, authHeader: String?): String? =
        authHeader?.removePrefix("Bearer ")
            ?: taskRequest.client?.let { getAccessToken(orgId, it) }

    private suspend fun getAccessToken(client: String, orgId: String): String? =
        flaisGateway.getAuthObject(orgId, client)
            ?.let { idpClient.getTokenResponse(it) }
            ?.accessToken

}