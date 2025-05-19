package no.fintlabs.linkwalker.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import java.time.Duration

@Configuration
class WebClientConfig(
    private val authProperties: AuthProperties
) {

    @Bean("authWebClient")
    fun authWebClient() =
        WebClient.builder()
            .baseUrl(authProperties.idpUri)
            .build()

    @Bean("flaisWebClient")
    fun flaisWebClient() =
        WebClient.builder()
            .baseUrl(authProperties.flaisGateway)
            .build()

    @Bean
    fun webClient(): WebClient {
        val provider = ConnectionProvider.builder("linkwalker-pool")
            .maxConnections(200)
            .maxIdleTime(Duration.ofSeconds(15))
            .maxLifeTime(Duration.ofMinutes(2))
            .pendingAcquireTimeout(Duration.ofSeconds(30))
            .evictInBackground(Duration.ofSeconds(30))
            .build()

        val httpClient = HttpClient.create(provider)
            .compress(true)
            .responseTimeout(Duration.ofSeconds(60))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
            .doOnConnected { c ->
                c.addHandlerLast(ReadTimeoutHandler(60))
                    .addHandlerLast(WriteTimeoutHandler(60))
            }

        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .exchangeStrategies(
                ExchangeStrategies.builder()
                    .codecs { it.defaultCodecs().maxInMemorySize(-1) }
                    .build()
            )
            .build()
    }
}
