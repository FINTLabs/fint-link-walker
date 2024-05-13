package no.fintlabs.linkwalker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.fintlabs.linkwalker.client.Client;
import no.fintlabs.linkwalker.client.ClientEvent;
import no.fintlabs.linkwalker.client.ClientService;
import no.fintlabs.linkwalker.task.model.EntryReport;
import no.fintlabs.linkwalker.task.model.RelationError;
import no.fintlabs.linkwalker.request.RequestService;
import no.fintlabs.linkwalker.request.model.Entry;
import no.fintlabs.linkwalker.task.model.Task;
import no.fintlabs.linkwalker.task.TaskCache;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

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
    private final ClientService clientService;
    private final SecretService secretService;

    public void processTask(Task task) {
        initializeTaskWithToken(task)
                .thenAcceptAsync(hasToken -> {
                    if (hasToken) fetchResources(task);
                })
                .exceptionally(e -> {
                    log.error("Error processing task: {}", e.getMessage());
                    task.setStatus(Task.Status.FAILED);
                    return null;
                });
    }

    private CompletableFuture<Boolean> initializeTaskWithToken(Task task) {
        if (task.getToken() != null) {
            return CompletableFuture.completedFuture(true);
        }

        return clientService.get(task.getClient(), task.getOrg())
                .map(clientEvent -> handleClientEvent(clientEvent, task))
                .orElseGet(() -> {
                    log.error("Client not found");
                    return CompletableFuture.completedFuture(false);
                });
    }

    private CompletableFuture<Boolean> handleClientEvent(ClientEvent clientEvent, Task task) {
        if (clientEvent.hasError()) {
            log.error("Found client.. but it has an error!!");
            log.error(clientEvent.getErrorMessage());
            return CompletableFuture.completedFuture(false);
        }

        Client client = clientEvent.getObject();
        return requestService.getToken(
                        client.getName(),
                        secretService.decrypt(client.getPassword()),
                        client.getClientId(),
                        secretService.decrypt(client.getClientSecret())
                ).toFuture()
                .thenApply(tokenResponse -> {
                    task.setToken(tokenResponse.access_token());
                    return true;
                });
    }

    private void fetchResources(Task task) {
        task.setStatus(Task.Status.FETCHING_RESOURCES);
        requestService.fetchFintResources(task.getUrl(), task.getToken()).subscribe(fintResources -> {
            createEntryReports(task, fintResources._embedded()._entries());
            processLinks(task);
        });
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
