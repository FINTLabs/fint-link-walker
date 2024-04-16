package no.fintlabs.linkwalker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.fintlabs.FintCustomerObjectEvent;
import no.fintlabs.linkwalker.client.Client;
import no.fintlabs.linkwalker.client.ClientEvent;
import no.fintlabs.linkwalker.client.ClientEventRequestProducerService;
import no.fintlabs.linkwalker.client.ClientService;
import no.fintlabs.linkwalker.report.model.EntryReport;
import no.fintlabs.linkwalker.report.model.RelationError;
import no.fintlabs.linkwalker.request.RequestService;
import no.fintlabs.linkwalker.request.model.Entry;
import no.fintlabs.linkwalker.task.Task;
import no.fintlabs.linkwalker.task.TaskCache;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Async
@Component
@Slf4j
@RequiredArgsConstructor
public class LinkWalker {

    private final RequestService requestService;
    private final TaskCache taskCache;
    private final ClientService clientService;

    public void processTask(Task task) {
        if (task.getToken() == null) {
            Optional<ClientEvent> optionalClient = clientService.get(task.getClientName(), task.getOrg());

            if (optionalClient.isPresent()) {
                ClientEvent clientEvent = optionalClient.get();
                if (clientEvent.hasError()) {
                    log.error("Found client.. but it has an error!!");
                    log.error(clientEvent.getErrorMessage());
                    return;
                } else {
                    log.info("ITS HERE!! {}", clientEvent.getObject().toString());
                    Client object = clientEvent.getObject();
                    log.info(object.getName());
                    log.info(object.getClientId());
                    log.info(object.getClientSecret());
                    log.info(object.getPassword());
                }
            } else {
                log.error("Client not found");
                return;
            }
        }

        fetchResources(task);
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
            if (taskCache.exists(task.getOrg(), task.getId())) {
                requestService.fetchStatusCode(url, task.getToken()).subscribe(httpStatusCode -> {
                    task.decrementRequest();
                    if (task.getRequests().get() == 0) {
                        task.setStatus(Task.Status.COMPLETED);
                    }
                    if (httpStatusCode.isError()) {
                        entryReport.addRelationError(new RelationError(url, httpStatusCode.value()));
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
