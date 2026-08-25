package no.novari.linkwalker.auth.model

data class FintCredentials(
    val clientId: String,
    val clientSecret: String,
    val username: String,
    val password: String,
)
