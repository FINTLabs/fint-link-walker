package no.fintlabs.linkwalker.config

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import no.fintlabs.linkwalker.task.model.Task
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CaffeineConfig {

    @Bean
    fun taskCache(): Cache<String, Task> =
        Caffeine.newBuilder()
            .build()

}