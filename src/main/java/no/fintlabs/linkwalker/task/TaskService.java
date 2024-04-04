package no.fintlabs.linkwalker.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.fintlabs.linkwalker.LinkWalker;
import org.springframework.stereotype.Service;

import java.util.*;

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

    public Collection<Task> getCache(String organization) {
        return Optional.ofNullable(organizationCache.get(organization))
                .map(Map::values)
                .orElse(Collections.emptyList());
    }

    public void clearCache(String organization) {
        if (organizationCache.containsKey(organization)) {
            organizationCache.get(organization).clear();
        }
    }

}
