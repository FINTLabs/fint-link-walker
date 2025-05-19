package no.fintlabs.linkwalker.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties("fint.linkwalker.auth")
data class AuthProperties(
    val enabled: Boolean = false,
    val idpUri: String = "https://idp.felleskomponent.no/nidp/oauth/nam/token"
)