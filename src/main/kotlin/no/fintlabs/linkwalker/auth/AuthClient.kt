package no.fintlabs.linkwalker.auth

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class AuthClient(
    @Qualifier("authWebClient")
    private val webClient: WebClient
) {



}