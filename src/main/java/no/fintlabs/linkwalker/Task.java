package no.fintlabs.linkwalker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import no.fintlabs.linkwalker.report.model.EntryReport;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties({"token", "entryReports"})
public class Task {

    private final String id = UUID.randomUUID().toString();
    private final String url;
    private final String env;
    private final String uri;
    private final String clientName;
    private final Set<String> filter;
    private final AtomicInteger requests = new AtomicInteger(0);
    private final List<EntryReport> entryReports = new ArrayList<>();
    private final String time = getFormattedTime();
    private int totalRequests;
    private String token;
    private Status status;
    private String org;

    public Task(String url, String clientName, Set<String> filter) {
        this.url = url;
        this.env = url.split("//")[1].split("\\.")[0];
        this.uri = url.split("\\.no")[1];
        this.clientName = clientName;
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
