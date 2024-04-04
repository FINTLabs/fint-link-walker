package no.fintlabs.linkwalker.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.fintlabs.linkwalker.LinkWalker;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final LinkWalker linkWalker;
    private final Map<String, Map<String, Task>> organizationCache = new HashMap<>();

    public void startTask(Task task) {
        organizationCache.putIfAbsent(task.getOrg(), new HashMap<>());
        organizationCache.get(task.getOrg()).put(task.getOrg(), task);
        linkWalker.processTask(task);
    }

}
