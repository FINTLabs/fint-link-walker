package no.fintlabs.linkwalker.config

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import no.fintlabs.linkwalker.task.model.Task
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ChannelConfig {

    @Bean
    fun taskChannel(): Channel<Pair<Task, String>> =
        Channel(Channel.UNLIMITED)

    @Bean
    @OptIn(ExperimentalCoroutinesApi::class)
    fun taskProcessorDispatcher(): CoroutineDispatcher =
        Dispatchers.IO.limitedParallelism(1)
}