package no.fintlabs.linkwalker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final LinkWalker linkWalker;
    private final TaskCache taskCache;

    public void startTask(Task task, String organization, String authHeader) {
        task.setStatus(Task.Status.STARTED);
        task.setOrg(organization);
        if (authHeader != null) task.setToken(authHeader.replace("Bearer ", ""));

        taskCache.add(task);
        linkWalker.processTask(task);
    }

    public Optional<Task> getTask(String organization, String id) {
        return taskCache.get(organization, id);
    }

    public Collection<Task> getTasks(String organization) {
        return taskCache.getAll(organization)
                .orElse(Collections.emptyList());
    }

    public void clearCache(String organization) {
        taskCache.clearOrganization(organization);
    }

    public void clearTask(String organization, String id) {
        taskCache.removeTask(organization, id);
    }

}
