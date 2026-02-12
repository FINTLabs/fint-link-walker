package no.fintlabs.linkwalker.auth

import no.fintlabs.linkwalker.task.model.TaskRequest
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val flaisGateway: FlaisGateway,
    private val idpClient: IdpClient
) {

    suspend fun getBearerToken(orgId: String, taskRequest: TaskRequest, authHeader: String?): String? =
        listOfNotNull(
            taskRequest.url.takeIf { it.contains("pwf", true) }?.let { "" },
            authHeader?.removePrefix("Bearer ")?.trim(),
            getAccessToken(orgId)
        ).firstOrNull()

    private suspend fun getAccessToken(orgId: String): String? =
        flaisGateway.getAuthObject(orgId)
            ?.let { idpClient.getTokenResponse(it) }
            ?.accessToken

}