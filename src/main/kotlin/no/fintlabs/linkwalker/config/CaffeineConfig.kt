package no.fintlabs.linkwalker.config

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import no.fintlabs.linkwalker.model.RelationError
import no.fintlabs.linkwalker.task.model.Task
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class CaffeineConfig {

    @Bean
    fun taskCache(): Cache<String, Task> =
        Caffeine.newBuilder()
            .build()

    @Bean
    fun relationErrorCache(): Cache<String, MutableList<RelationError>> =
        Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofHours(1))
            .build()

}