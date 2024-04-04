package no.fintlabs.linkwalker.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public ResponseEntity<?> postTask(@RequestBody Task task,
                                      @RequestHeader(value = "Authorization", required = false) String authHeader,
                                      ServerWebExchange webExchange) {
        if (requestNotValid(task, authHeader)) {
            return ResponseEntity.badRequest().body("Client & organization are required or a valid Bearer token in header");
        }
        taskService.startTask(task);
        return ResponseEntity.created(createIdUri(webExchange, task.getId())).body(task);
    }

    public boolean requestNotValid(Task task, String authHeader) {
        return task.getUrl() == null || task.getOrg() == null || (authHeader == null && task.getClientName() == null);
    }

    private URI createIdUri(ServerWebExchange webExchange, String id) {
        return UriComponentsBuilder.fromUriString(webExchange.getRequest().getURI().toString())
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
    }

}
