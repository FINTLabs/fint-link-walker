package no.fintlabs.linkwalker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("link-walker")
data class LinkWalkerConfig (
    val components: List<String> = listOf(),
    val maxAttempts: Long = 5L
) {
    init {
        // Ensure that the components does not contain the characters '-', '.' or any whitespace
        require(components.all { !it.contains("-|\\.|\\s") })
    }
}