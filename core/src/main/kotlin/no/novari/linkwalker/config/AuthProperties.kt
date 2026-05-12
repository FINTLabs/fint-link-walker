package no.novari.linkwalker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("fint.link-walker.auth")
data class AuthProperties(
    val idpUri: String = "https://idp.felleskomponent.no/nidp/oauth/nam/token",
    val flaisGateway: String = "http://fint-customer-objects-gateway.flais-io.svc.cluster.local:8080"
)