package no.novari.linkwalker.auth.model

import com.fasterxml.jackson.annotation.JsonProperty

data class TokenResponse(
    @field:JsonProperty("access_token")
    val accessToken: String,
    @field:JsonProperty("token_type")
    val tokenType: String,
    @field:JsonProperty("expires_in")
    val expiresIn: Int,
    val acr: String,
    val scope: String
)
