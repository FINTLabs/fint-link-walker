package no.novari.linkwalker.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient

@Configuration
class HttpClientConfig(
    private val authProperties: AuthProperties,
    private val linkWalkerConfig: LinkWalkerConfig,
) {

    @Bean("idpRestClient")
    fun idpRestClient(): RestClient =
        RestClient.builder()
            .baseUrl(authProperties.idpUri)
            .requestFactory(jdkRequestFactory())
            .build()

    @Bean("flaisRestClient")
    fun flaisRestClient(): RestClient =
        RestClient.builder()
            .baseUrl(authProperties.flaisGateway)
            .requestFactory(jdkRequestFactory())
            .build()

    @Bean
    fun fintRestClient(): RestClient =
        RestClient.builder()
            .defaultHeader("x-fint-model-version-override", "V4")
            .requestFactory(jdkRequestFactory())
            .build()

    private fun jdkRequestFactory(): JdkClientHttpRequestFactory {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(linkWalkerConfig.connectTimeout)
            .build()
        return JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(linkWalkerConfig.readTimeout)
        }
    }
}
