package no.fintlabs.linkwalker.task

import no.fintlabs.linkwalker.auth.AuthService
import no.fintlabs.linkwalker.task.model.TaskRequest
import org.springframework.stereotype.Service

@Service
class TaskService(
    private val authService: AuthService
) {

    fun initialiseTask(taskRequest: TaskRequest, authHeader: String?) {
        authService.getBearerToken(authHeader, taskRequest.client)?.let { bearerToken ->

        }
    }

}