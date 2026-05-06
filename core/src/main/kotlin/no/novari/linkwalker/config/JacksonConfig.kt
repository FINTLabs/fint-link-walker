package no.novari.linkwalker.config

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JacksonConfig {

    @Bean
    @ConditionalOnMissingBean
    fun objectMapper(): ObjectMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .build()
}
