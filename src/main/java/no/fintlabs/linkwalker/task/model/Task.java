package no.fintlabs.linkwalker.task.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties({"token", "entryReports", "formattedTime", "authHeader"})
public class Task {

    private final String id = UUID.randomUUID().toString();
    private final String url;
    private final String env;
    private final String uri;
    private final String client;
    private final Set<String> filter;
    private final AtomicInteger requests = new AtomicInteger(0);
    private final List<EntryReport> entryReports = new ArrayList<>();
    private final String time = getFormattedTime();
    private AtomicInteger relationErrors = new AtomicInteger(0);
    private AtomicInteger healthyRelations = new AtomicInteger(0);
    private int totalRequests;
    private String authHeader;
    private String token;
    private Status status;
    private String org;
    private String errorMessage;

    public Task(String url, String client, Set<String> filter) {
        this.url = url;
        this.env = url.split("//")[1].split("\\.")[0];
        this.uri = url.split("\\.no")[1];
        this.client = client;
        this.filter = filter;
    }

    public void incrementTotalRequest(Integer count) {
        totalRequests += count;
    }

    public void incrementRequest() {
        requests.incrementAndGet();
    }

    public void decrementRequest() {
        requests.decrementAndGet();
    }

    public void addEntryReport(EntryReport entryReport) {
        entryReports.add(entryReport);
    }

    public String getFormattedTime() {
        String pattern = "dd/MM HH:mm";
        SimpleDateFormat dateFormatter = new SimpleDateFormat(pattern);
        return dateFormatter.format(new Date());
    }

    public enum Status {
        STARTED,
        FETCHING_RESOURCES,
        CREATING_ENTRY_REPORTS,
        PROCESSING_LINKS,
        COMPLETED,
        FAILED
    }

}
