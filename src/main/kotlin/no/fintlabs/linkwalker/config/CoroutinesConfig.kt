package no.fintlabs.linkwalker.config

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CoroutinesConfig {

    @Bean
    fun applicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Bean
    @OptIn(ExperimentalCoroutinesApi::class)
    fun taskProcessorDispatcher(): CoroutineDispatcher =
        Dispatchers.IO.limitedParallelism(1)

}