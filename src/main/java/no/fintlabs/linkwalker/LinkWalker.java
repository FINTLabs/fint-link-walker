package no.fintlabs.linkwalker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.fintlabs.linkwalker.request.RequestService;
import no.fintlabs.linkwalker.task.Task;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Async
@Component
@Slf4j
@RequiredArgsConstructor
public class LinkWalker {

    private final RequestService requestService;

    public void processTask(Task task) {
        if (task.getToken() == null) {
            // Customer-Object-Gateway to fetch client credentials & Set token
        }

        requestService.fetchFintResources(task.getUrl(), task.getToken()).subscribe(fintResources -> {
            log.info(fintResources.toString());
        });

    }

}
