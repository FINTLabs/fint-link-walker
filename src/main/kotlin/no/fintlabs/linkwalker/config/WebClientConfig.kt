package no.fintlabs.linkwalker.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig {

    @Bean
    fun webclient() =
        WebClient.builder()
            .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "gzip")
            .build()

}