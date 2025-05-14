package no.fintlabs.linkwalker.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
class WebClientConfig {

    @Bean
    fun webClientBuilder(): WebClient.Builder =
        WebClient.builder()
            .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "gzip")
            .clientConnector(
                ReactorClientHttpConnector(
                    HttpClient.create()
                        .responseTimeout(Duration.ofMinutes(5))
                )
            )

}