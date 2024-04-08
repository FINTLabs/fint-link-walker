package no.fintlabs.linkwalker.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Data
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties({"token"})
public class Task {

    private final String id = UUID.randomUUID().toString();
    private final String url;
    private final String clientName;
    private final Set<String> filter;
    private final AtomicInteger requests = new AtomicInteger(0);
    private String org;
    private String token;
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
