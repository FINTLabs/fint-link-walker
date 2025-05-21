package no.fintlabs.linkwalker.auth.model

import com.fasterxml.jackson.annotation.JsonProperty

data class AuthResponse(
    @JsonProperty("object")
    val authObject: AuthObject,
    val orgId: String,
    val operation: String,
    val errorMessage: String?
)
