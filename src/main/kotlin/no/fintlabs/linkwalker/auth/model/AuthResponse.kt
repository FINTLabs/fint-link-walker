package no.fintlabs.linkwalker.auth.model

import com.fasterxml.jackson.annotation.JsonProperty

data class AuthResponse(
    // Auth object is only null if the client does not exist
    @get:JsonProperty("object")
    val authObject: AuthObject?,
    val orgId: String,
    val operation: String,
    val errorMessage: String?
)
