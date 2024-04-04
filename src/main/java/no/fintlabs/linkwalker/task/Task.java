package no.fintlabs.linkwalker.task;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Data
@AllArgsConstructor
public class Task {

    private final String id = UUID.randomUUID().toString();
    private final String url;
    private final String clientName;
    private final String org;
    private final String token;
    private final Set<String> filter;
    private final AtomicInteger requests = new AtomicInteger(0);
    private Status status;

    public enum Status {
        STARTED,
        FETCHING_RESOURCES,
        CREATING_ENTRY_REPORTS,
        COUNTING_REQUESTS,
        PROCESSING_LINKS,
        COMPLETED,
        FAILED
    }

}
