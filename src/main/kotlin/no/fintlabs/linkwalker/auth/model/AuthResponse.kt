package no.fintlabs.linkwalker.auth.model

data class AuthResponse(
    val `object`: AuthObject,
    val orgId: String,
    val operation: String
)
