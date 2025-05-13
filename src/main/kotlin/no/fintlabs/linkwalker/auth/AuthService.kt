package no.fintlabs.linkwalker.auth

import org.springframework.stereotype.Service

@Service
class AuthService {

    fun getBearerToken(authHeader: String, client: String?): String? =
        authHeader.removePrefix("Bearer ")

}