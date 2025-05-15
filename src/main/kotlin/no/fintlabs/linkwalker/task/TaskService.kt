package no.fintlabs.linkwalker.task

import com.github.benmanes.caffeine.cache.Cache
import no.fintlabs.linkwalker.LinkwalkerService
import no.fintlabs.linkwalker.auth.AuthService
import no.fintlabs.linkwalker.task.model.Task
import no.fintlabs.linkwalker.task.model.TaskRequest
import org.springframework.stereotype.Service

@Service
class TaskService(
    private val authService: AuthService,
    private val taskCache: Cache<String, Task>,
    private val linkwalker: LinkwalkerService
) {

    suspend fun initialiseTask(taskRequest: TaskRequest, authHeader: String?): Task? =
        authService.getBearerToken(authHeader, taskRequest.client)?.let { bearerToken ->
            val task = Task(taskRequest.url)

            taskCache.put(task.id, task)
            linkwalker.processTask(task, bearerToken)

            task
        }

}