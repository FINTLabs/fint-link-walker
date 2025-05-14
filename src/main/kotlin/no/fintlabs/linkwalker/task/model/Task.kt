package no.fintlabs.linkwalker.task.model

import java.util.*

data class Task(
    val url: String
) {
    val id = UUID.randomUUID().toString()
}