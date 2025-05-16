package no.fintlabs.linkwalker.task

import no.fintlabs.linkwalker.ExcelService
import no.fintlabs.linkwalker.RelationErrorService
import no.fintlabs.linkwalker.task.model.Task
import no.fintlabs.linkwalker.task.model.TaskRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/tasks/{orgId}")
class TaskController(
    private val taskService: TaskService,
    private val excelService: ExcelService,
    private val relationErrorService: RelationErrorService
) {

    @PostMapping
    fun createTask(
        @PathVariable orgId: String,
        @RequestBody taskRequest: TaskRequest,
        @RequestHeader(value = "Authorization", required = false) authHeader: String?
    ) = taskService.initialiseTask(orgId, taskRequest, authHeader)
        ?.let { ResponseEntity.accepted().body(it) }
        ?: ResponseEntity.badRequest().build()

    @GetMapping
    fun getTasks(@PathVariable orgId: String): ResponseEntity<Collection<Task>> =
        taskService.getTasks(orgId).let { ResponseEntity.ok(it) }

    @GetMapping("/{id}")
    fun getTask(@PathVariable orgId: String, @PathVariable id: String): ResponseEntity<Task> =
        taskService.getTask(id)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @GetMapping("/{id}/download")
    suspend fun downloadExcelReport(@PathVariable id: String): ResponseEntity<ByteArray?> =
        taskService.getTask(id)?.let { task ->
            ResponseEntity(
                excelService.createSpreadSheet(relationErrorService.get(id)),
                HttpHeaders().apply {
                    contentType = MediaType.APPLICATION_OCTET_STREAM
                    setContentDispositionFormData("attachment", "relasjonstest-${task.time}.xlsx")
                },
                HttpStatus.OK
            )
        } ?: ResponseEntity.notFound().build()


    @PutMapping("/{id}")
    fun clearTask(@PathVariable id: String): ResponseEntity<Any> =
        taskService.clearTask(id)
            ?.let { ResponseEntity.ok().build() }
            ?: ResponseEntity.notFound().build()


    @PutMapping
    fun clearTasks(@PathVariable orgId: String): ResponseEntity<Any> =
        taskService.clearTasks(orgId).let { ResponseEntity.ok().build() }

}