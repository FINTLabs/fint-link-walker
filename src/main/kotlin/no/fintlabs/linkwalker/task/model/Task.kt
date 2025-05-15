package no.fintlabs.linkwalker.task.model

import no.fintlabs.linkwalker.model.Status
import java.util.*

data class Task(
    val url: String,
    var relations: Int = 0,
    var relationErrors: Int = 0,
    var status: Status = Status.STARTED
) {
    val id = UUID.randomUUID().toString()
}