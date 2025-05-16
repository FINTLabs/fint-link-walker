package no.fintlabs.linkwalker.config

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import no.fintlabs.linkwalker.model.RelationReport
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
    fun relationErrorCache(): AsyncCache<String, MutableList<RelationReport>> =
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(4))
            .maximumSize(10_000)
            .buildAsync()

}