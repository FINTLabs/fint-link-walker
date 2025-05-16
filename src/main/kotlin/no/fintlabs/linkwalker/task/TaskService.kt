package no.fintlabs.linkwalker.task

import com.github.benmanes.caffeine.cache.Cache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import no.fintlabs.linkwalker.LinkwalkerService
import no.fintlabs.linkwalker.auth.AuthService
import no.fintlabs.linkwalker.model.Status
import no.fintlabs.linkwalker.task.model.Task
import no.fintlabs.linkwalker.task.model.TaskRequest
import org.springframework.stereotype.Service

@Service
class TaskService(
    private val authService: AuthService,
    private val cache: Cache<String, Task>,
    private val linkwalker: LinkwalkerService,
    private val applicationScope: CoroutineScope
) {

    fun initialiseTask(orgId: String, taskRequest: TaskRequest, authHeader: String?): Task? =
        authService.getBearerToken(authHeader, taskRequest.client)?.let { bearerToken ->
            val task = Task(taskRequest.url, orgId)

            cache.put(task.id, task)
            applicationScope.launch {
                task.status = Status.PROCESSING
                linkwalker.processTask(task, bearerToken)
                task.status = Status.FINISHED
            }

            task
        }

    fun getTasks(orgId: String): Collection<Task> =
        cache.asMap()
            .values
            .filter { it.orgId.equals(orgId, ignoreCase = true) }

}