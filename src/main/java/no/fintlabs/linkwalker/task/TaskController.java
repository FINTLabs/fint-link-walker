package no.fintlabs.linkwalker.task;

import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.fintlabs.linkwalker.SpreadsheetService;
import no.fintlabs.linkwalker.task.model.Task;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Collection;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/tasks/{organization}")
public class TaskController {

    private final TaskService taskService;
    private final SpreadsheetService spreadsheetService;

    @PostMapping
    public ResponseEntity<?> postTask(@PathVariable String organization,
                                      @RequestBody Task task,
                                      @RequestHeader(value = "Authorization", required = false) String authHeader,
                                      ServerWebExchange webExchange) {
        if (requestNotValid(task, authHeader)) {
            log.debug("The requested taks is not valid{}", task);
            return ResponseEntity.badRequest().body(badRequestMessage(task));
        }
        taskService.startTask(task, organization, authHeader);
        return ResponseEntity.created(createIdUri(webExchange, task.getId())).body(task);
    }

    @GetMapping
    public ResponseEntity<Collection<Task>> getTasks(@PathVariable String organization) {
        return ResponseEntity.ok(taskService.getTasks(organization));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Task> getTask(@PathVariable String organization, @PathVariable String id) {
        return taskService.getTask(organization, id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadRelationErrors(@PathVariable String organization, @PathVariable String id) {
        Optional<Task> optionalTask = taskService.getTask(organization, id);

        if (optionalTask.isPresent()) {
            Task task = optionalTask.get();
            byte[] spreadsheet = spreadsheetService.createSpreadSheet(task.getEntryReports());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", String.format("relasjonstest-%s.xlsx", task.getTime()));

            return new ResponseEntity<>(spreadsheet, headers, HttpStatus.OK);
        }

        return ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> clearTask(@PathVariable String organization, @PathVariable String id) {
        taskService.clearTask(organization, id);
        return ResponseEntity.ok().build();
    }


    @PutMapping
    public ResponseEntity<?> clearTasks(@PathVariable String organization) {
        taskService.clearCache(organization);
        return ResponseEntity.ok().build();
    }

    private String badRequestMessage(Task task) {
        return task.getUrl() == null
                ? "Url is required in body"
                : "Client is required in body or a Bearer <token> in Authorization header";
    }

    public boolean requestNotValid(Task task, String authHeader) {
        return task.getUrl() == null || (authHeader == null && StringUtils.isEmpty(task.getClient()));
    }

    private URI createIdUri(ServerWebExchange webExchange, String id) {
        return UriComponentsBuilder.fromUriString(webExchange.getRequest().getURI().toString())
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
    }

}
