package no.fintlabs.linkwalker.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Value("${fint.gateway-url}")
    private String gatewayUrl;

    @Value("${fint.idp-url}")
    private String idpUrl;

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .exchangeStrategies(unlimitedBufferSize())
                .clientConnector(clientHttpConnector())
                .build();
    }

    @Bean
    public WebClient gatewayWebClient() {
        return WebClient.builder()
                .baseUrl(gatewayUrl)
                .build();
    }

    @Bean
    public WebClient idpWebClient(){
        return WebClient.builder()
                .baseUrl(idpUrl)
                .build();
    }

    private ExchangeStrategies unlimitedBufferSize() {
        return ExchangeStrategies.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(-1))
                .build();

    }

    public ClientHttpConnector clientHttpConnector() {
        return new ReactorClientHttpConnector(HttpClient.create(
                        ConnectionProvider
                                .builder("laidback")
                                .maxConnections(100)
                                .pendingAcquireMaxCount(-1)
                                .pendingAcquireTimeout(Duration.ofMinutes(15))
                                .maxLifeTime(Duration.ofMinutes(30))
                                .maxIdleTime(Duration.ofMinutes(5))
                                .build())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 900000)
                .responseTimeout(Duration.ofMinutes(10))
        );
    }

}
