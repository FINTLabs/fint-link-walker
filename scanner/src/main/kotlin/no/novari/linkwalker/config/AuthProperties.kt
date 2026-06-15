package no.novari.linkwalker.config

import no.novari.linkwalker.auth.model.FintCredentials
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("fint.link-walker.auth")
data class AuthProperties(
    val idpUri: String = "https://idp.felleskomponent.no/nidp/oauth/nam/token",
    val flaisGateway: String = "http://fint-customer-objects-gateway.flais-io.svc.cluster.local:8080",
    val credentials: CredentialsProperties = CredentialsProperties(),
)

data class CredentialsProperties(
    val clientId: String? = null,
    val clientSecret: String? = null,
    val username: String? = null,
    val password: String? = null,
) {
    init {
        val present = listOf(clientId, clientSecret, username, password).count { !it.isNullOrBlank() }
        require(present == 0 || present == 4) {
            "fint.link-walker.auth.credentials is partially configured ($present/4 set); " +
                "set all of client-id, client-secret, username and password, or none"
        }
    }

    fun toFintCredentialsOrNull(): FintCredentials? =
        if (clientId.isNullOrBlank()) {
            null
        } else {
            FintCredentials(
                clientId = clientId.trim(),
                clientSecret = clientSecret!!.trim(),
                username = username!!.trim(),
                password = password!!.trim(),
            )
        }
}
