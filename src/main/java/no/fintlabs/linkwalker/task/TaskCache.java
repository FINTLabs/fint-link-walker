package no.fintlabs.linkwalker.task;

import no.fintlabs.linkwalker.task.model.Task;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class TaskCache {

    private final Map<String, Map<String, Task>> taskCache = new HashMap<>();

    public void add(Task task) {
        taskCache.putIfAbsent(task.getOrg(), new HashMap<>());
        taskCache.get(task.getOrg()).put(task.getId(), task);
    }

    public Optional<Task> get(String organization, String id) {
        return Optional.ofNullable(taskCache.get(organization).get(id));
    }

    public Optional<Collection<Task>> getAll(String organization) {
        return taskCache.containsKey(organization)
                ? Optional.of(taskCache.get(organization).values())
                : Optional.empty();
    }

    public void clearOrganization(String organization) {
        taskCache.put(organization, new HashMap<>());
    }

    public void removeTask(String organization, String id) {
        if (taskCache.containsKey(organization)) {
            taskCache.get(organization).remove(id);
        }
    }

    public boolean active(String organization, String id) {
        if (!taskCache.containsKey(organization)) {
            return false;
        }

        Task task = taskCache.get(organization).get(id);

        return task != null && task.getStatus() != Task.Status.FAILED;
    }

}
