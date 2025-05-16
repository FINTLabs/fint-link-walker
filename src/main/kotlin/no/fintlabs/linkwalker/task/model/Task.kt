package no.fintlabs.linkwalker.task.model

import java.net.URI
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

data class Task(
    val url: String,
    val orgId: String,
    var totalRequests: Int = 0,
    var relationErrors: AtomicInteger = AtomicInteger(0),
    var status: Status = Status.IN_QUEUE
) {

    private val parsed = URI(url)

    val id: String = UUID.randomUUID().toString()
    val env: String = parsed.host.substringBefore('.')
    val uri: String = parsed.path.removePrefix("/")
    val time: String = SimpleDateFormat("dd/MM HH:mm").format(Date())
    var errorMessage: String? = null

    val healthyRelations get() = totalRequests - relationErrors.get()
}