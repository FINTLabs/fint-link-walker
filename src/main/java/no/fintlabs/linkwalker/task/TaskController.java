package no.fintlabs.linkwalker.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @PostMapping
    public ResponseEntity<?> postTask(@PathVariable String organization,
                                      @RequestBody Task task,
                                      @RequestHeader(value = "Authorization", required = false) String authHeader,
                                      ServerWebExchange webExchange) {
        if (requestNotValid(task, authHeader)) {
            log.info("The request is not valid for client: {} ", task.getClientName());
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
        return task.getUrl() == null || (authHeader == null && task.getClientName() == null);
    }

    private URI createIdUri(ServerWebExchange webExchange, String id) {
        return UriComponentsBuilder.fromUriString(webExchange.getRequest().getURI().toString())
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
    }

}
