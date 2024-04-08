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

    public void startTask(Task task, String organization, String authHeader) {
        task.setStatus(Task.Status.STARTED);
        task.setOrg(organization);
        if (authHeader != null) task.setToken(authHeader.replace("Bearer ", ""));

        organizationCache.putIfAbsent(task.getOrg(), new HashMap<>());
        organizationCache.get(task.getOrg()).put(task.getOrg(), task);
        linkWalker.processTask(task);
    }

    public Optional<Task> getTask(String organization, String id) {
        return Optional.ofNullable(organizationCache.getOrDefault(organization, new HashMap<>()).getOrDefault(id, null));
    }

    public Collection<Task> getTasks(String organization) {
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
