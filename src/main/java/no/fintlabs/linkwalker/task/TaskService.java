package no.fintlabs.linkwalker.task;

import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.fintlabs.linkwalker.LinkWalker;
import no.fintlabs.linkwalker.task.model.Task;
import org.apache.poi.util.StringUtil;
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
        task.setAuthHeader(authHeader);

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
