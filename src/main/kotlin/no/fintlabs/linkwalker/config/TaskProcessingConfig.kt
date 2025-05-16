package no.fintlabs.linkwalker.config

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TaskProcessingConfig {

    @Bean
    @OptIn(ExperimentalCoroutinesApi::class)
    fun taskProcessorDispatcher(): CoroutineDispatcher =
        Dispatchers.IO.limitedParallelism(1)

}