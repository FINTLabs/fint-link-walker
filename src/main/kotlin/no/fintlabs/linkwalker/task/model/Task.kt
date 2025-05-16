package no.fintlabs.linkwalker.task.model

import no.fintlabs.linkwalker.model.Status
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

data class Task(
    val url: String,
    val orgId: String,
    var relations: Int = 0,
    var relationErrors: AtomicInteger = AtomicInteger(0),
    var status: Status = Status.IN_QUEUE
) {
    val id = UUID.randomUUID().toString()
    val healthyRelations get() = relations - relationErrors.get()
}