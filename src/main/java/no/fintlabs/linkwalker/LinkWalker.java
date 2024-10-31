package no.fintlabs.linkwalker;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.fintlabs.linkwalker.auth.AuthService;
import no.fintlabs.linkwalker.request.RequestService;
import no.fintlabs.linkwalker.request.model.Entry;
import no.fintlabs.linkwalker.task.TaskCache;
import no.fintlabs.linkwalker.task.model.EntryReport;
import no.fintlabs.linkwalker.task.model.RelationError;
import no.fintlabs.linkwalker.task.model.Task;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Async
@Component
@Slf4j
@RequiredArgsConstructor
public class LinkWalker {

    private final RequestService requestService;
    private final TaskCache taskCache;
    private final AuthService authService;

    public void processTask(Task task) {
        initializeTaskWithToken(task)
                .thenAcceptAsync(hasToken -> {
                    if (hasToken) {
                        fetchResources(task);
                    } else {
                        log.error("Could not get Token");
                        task.setStatus(Task.Status.FAILED);
                    };
                })
                .exceptionally(e -> {
                    log.error("Error processing task: {}", e.getMessage());
                    task.setStatus(Task.Status.FAILED);
                    return null;
                });
    }

    private CompletableFuture<Boolean> initializeTaskWithToken(Task task) {
        if (task.getUrl().contains("play-with-fint")) {
            return CompletableFuture.completedFuture(true);
        }

        if (StringUtils.isEmpty(task.getClient())){
            log.info("Client not set, using bearer");
            task.setToken(task.getAuthHeader().replace("Bearer ", ""));
        }

        if (task.getToken() != null) {
            return CompletableFuture.completedFuture(true);
        }

        try {
            authService.applyNewAccessToken(task);
            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            throw e;
        }
    }

    private void fetchResources(Task task) {
        task.setStatus(Task.Status.FETCHING_RESOURCES);
        requestService.fetchFintResources(task.getUrl(), task.getToken())
                .subscribe(
                        fintResources -> {
                            createEntryReports(task, fintResources._embedded()._entries());
                            processLinks(task);
                        },
                        throwable -> {
                            String errorMessage = "";
                            if (throwable instanceof WebClientResponseException) {
                                WebClientResponseException webClientResponseException = (WebClientResponseException) throwable;
                                log.error("Error fetching resources for task: Status " + webClientResponseException.getStatusCode() +
                                        ", Body: " + webClientResponseException.getResponseBodyAsString(), webClientResponseException);
                                try {
                                    ObjectMapper objectMapper = new ObjectMapper();
                                    String responseBody = webClientResponseException.getResponseBodyAsString();
                                    JsonNode jsonNode = objectMapper.readTree(responseBody);

                                    errorMessage = "HTTP Status: " + webClientResponseException.getStatusCode() +
                                            ", Error Details: " + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
                                } catch (Exception e) {
                                    log.error("Failed to parse error response body: " + e.getMessage(), e);
                                    errorMessage = "HTTP Status: " + webClientResponseException.getStatusCode() +
                                            ", Error Body: " + webClientResponseException.getResponseBodyAsString();
                                }
                                task.setErrorMessage(errorMessage);
                            } else {
                                log.error("Error fetching resources for task: " + throwable.getMessage(), throwable);
                            }

                            task.setStatus(Task.Status.FAILED);
                        }
                );
    }


    private void processLinks(Task task) {
        task.setStatus(Task.Status.PROCESSING_LINKS);
        task.getEntryReports().forEach(entryReport -> {
            if (task.getFilter() != null && task.getFilter().contains("self")) {
                checkStatusCodes(entryReport.getSelfLinks(), entryReport, task);
            }
            checkStatusCodes(entryReport.getRelationLinks(), entryReport, task);
        });
    }

    private void checkStatusCodes(Set<String> links, EntryReport entryReport, Task task) {
        links.forEach(url -> {
            if (taskCache.active(task.getOrg(), task.getId())) {
                requestService.fetchStatusCode(url, task.getToken()).subscribe(httpStatusCode -> {
                    task.decrementRequest();
                    if (task.getRequests().get() == 0) {
                        task.setStatus(Task.Status.COMPLETED);
                    }
                    if (httpStatusCode.isError()) {
                        task.getRelationErrors().incrementAndGet();
                        entryReport.addRelationError(new RelationError(url, httpStatusCode.value()));
                    } else {
                        task.getHealthyRelations().incrementAndGet();
                    }
                });
            }
        });
    }

    private void createEntryReports(Task task, List<Entry> entries) {
        task.setStatus(Task.Status.CREATING_ENTRY_REPORTS);

        entries.forEach(entry -> {
            EntryReport entryReport = EntryReport.ofEntry(entry, task);
            task.addEntryReport(entryReport);
            task.incrementTotalRequest(entryReport.getRelationLinks().size());
        });
    }

}
