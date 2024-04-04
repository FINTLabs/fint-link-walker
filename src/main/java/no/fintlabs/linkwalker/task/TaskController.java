package no.fintlabs.linkwalker.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/tasks/{organization}")
public class TaskController {

    @PostMapping
    public void postTask(@PathVariable String organization, @RequestBody Task task) {
        log.info(task.toString());
    }

}
