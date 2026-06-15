package no.novari.linkwalker.auth

import no.novari.linkwalker.OrgId
import no.novari.linkwalker.auth.model.AuthObject
import no.novari.linkwalker.auth.model.FintCredentials
import no.novari.linkwalker.config.AuthProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val flaisGateway: FlaisGateway,
    private val idpClient: IdpClient,
    private val authProperties: AuthProperties,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun getBearerToken(orgId: OrgId): String? {
        val credentials = resolveCredentials(orgId) ?: return null
        return idpClient.getTokenResponse(credentials)?.accessToken
    }

    private suspend fun resolveCredentials(orgId: OrgId): FintCredentials? {
        authProperties.credentials.toFintCredentialsOrNull()?.let {
            logger.info("Using FINT credentials from configuration; skipping Flais gateway")
            return it
        }
        return flaisGateway.getAuthObject(orgId)?.toFintCredentials()
    }

    private fun AuthObject.toFintCredentials(): FintCredentials =
        FintCredentials(
            clientId = clientId,
            clientSecret = clientSecret,
            username = name,
            password = password,
        )
}
