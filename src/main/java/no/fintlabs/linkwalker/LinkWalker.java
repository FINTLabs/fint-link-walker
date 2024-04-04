package no.fintlabs.linkwalker;

import no.fintlabs.linkwalker.task.Task;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Async
@Component
public class LinkWalker {

    public void processTask(Task task) {

    }

}
