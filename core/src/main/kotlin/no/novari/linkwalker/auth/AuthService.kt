package no.novari.linkwalker.auth

import no.novari.linkwalker.OrgId
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val flaisGateway: FlaisGateway,
    private val idpClient: IdpClient,
) {

    suspend fun getBearerToken(orgId: OrgId): String? =
        flaisGateway.getAuthObject(orgId)
            ?.let { idpClient.getTokenResponse(it) }
            ?.accessToken
}
