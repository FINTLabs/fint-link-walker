package no.fintlabs.linkwalker.task

import com.github.benmanes.caffeine.cache.Cache
import kotlinx.coroutines.channels.Channel
import no.fintlabs.linkwalker.auth.AuthService
import no.fintlabs.linkwalker.model.LinkInfo
import no.fintlabs.linkwalker.task.model.Task
import no.fintlabs.linkwalker.task.model.TaskRequest
import org.springframework.stereotype.Service

@Service
class TaskService(
    private val authService: AuthService,
    private val cache: Cache<String, Task>,
    private val queue: Channel<Pair<Task, String>>
) {

    fun initialiseTask(orgId: String, taskRequest: TaskRequest, authHeader: String?): Task? =
        authService.getBearerToken(authHeader, taskRequest.client)?.let { bearer ->
            Task(taskRequest.url, orgId).also { task ->
                cache.put(task.id, task)
                queue.trySend(task to bearer)
            }
        }

    fun getTasks(orgId: String): Collection<Task> =
        cache.asMap()
            .values
            .filter { it.orgId.equals(orgId, ignoreCase = true) }

    fun updateRelationsCount(task: Task, relationCount: Int) =
        relationCount.let { task.totalRequests = it }

    fun addRelationError(task: Task, relationErrorCount: Int) =
        relationErrorCount.let { task.relationErrors.addAndGet(it) }

    fun getTask(taskId: String): Task? =
        cache.getIfPresent(taskId)

    fun clearTask(id: String): Task? =
        cache.asMap().remove(id)

    fun clearTasks(orgId: String) =
        cache.asMap().values
            .removeIf { it.orgId.equals(orgId, ignoreCase = true) }


}