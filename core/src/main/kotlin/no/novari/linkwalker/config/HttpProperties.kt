package no.novari.linkwalker.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("link-walker")
data class HttpProperties(
    val maxConcurrentFetches: Int = 50,
    val connectTimeout: Duration = Duration.ofSeconds(10),
    val readTimeout: Duration = Duration.ofMinutes(10),
    val maxAttempts: Int = 5,
)
