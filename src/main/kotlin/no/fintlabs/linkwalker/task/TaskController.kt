package no.fintlabs.linkwalker.task

import no.fintlabs.linkwalker.task.model.Task
import no.fintlabs.linkwalker.task.model.TaskRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/tasks/{orgId}")
class TaskController(
    private val taskService: TaskService
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

    //    @GetMapping("/{id}/download")
//    fun downloadRelationErrors(
//        @PathVariable organization: String?,
//        @PathVariable id: String?
//    ): ResponseEntity<ByteArray?> {
//        val optionalTask: Optional<Task> = taskService.getTask(organization, id)
//
//        if (optionalTask.isPresent()) {
//            val task: Task = optionalTask.get()
//            val spreadsheet: ByteArray? = spreadsheetService.createSpreadSheet(task.getEntryReports())
//
//            val headers = HttpHeaders()
//            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM)
//            headers.setContentDispositionFormData("attachment", String.format("relasjonstest-%s.xlsx", task.getTime()))
//
//            return ResponseEntity<ByteArray?>(spreadsheet, headers, HttpStatus.OK)
//        }
//
//        return ResponseEntity.notFound().build<ByteArray?>()
//    }
//
    @PutMapping("/{id}")
    fun clearTask(@PathVariable id: String): ResponseEntity<Any> =
        taskService.clearTask(id)
            ?.let { ResponseEntity.ok().build() }
            ?: ResponseEntity.notFound().build()
//
//
//    @PutMapping
//    fun clearTasks(@PathVariable organization: kotlin.String?): ResponseEntity<*> {
//        taskService.clearCache(organization)
//        return ResponseEntity.ok().build<Any?>()
//    }

}