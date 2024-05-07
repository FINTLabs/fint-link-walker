package no.fintlabs.linkwalker.task.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import no.fintlabs.linkwalker.request.model.Entry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@JsonIgnoreProperties({"relationLinks", "totalRequests"})
@Data
public class EntryReport {

    private final Set<String> selfLinks = new HashSet<>();
    private final Set<String> relationLinks = new HashSet<>();
    private final List<RelationError> relationErrors = new ArrayList<>();

    public boolean hasRelationError() {
        return !relationErrors.isEmpty();
    }

    public void addSelfLink(String parentLink) {
        selfLinks.add(parentLink);
    }

    public boolean addRelationLink(String relationLink) {
        return relationLinks.add(relationLink);
    }

    public void addRelationError(RelationError relationError) {
        relationErrors.add(relationError);
    }

    public static EntryReport ofEntry(Entry entry, Task task) {
        EntryReport entryReport = new EntryReport();

        entry._links().get("self").forEach(link -> {
            entryReport.addSelfLink(link.href());
        });

        entry._links().forEach((resourceName, links) -> {
            if (task.getFilter() == null || task.getFilter().contains(resourceName)) {
                links.forEach(link -> {
                    if (entryReport.addRelationLink(link.href())) {
                        task.incrementRequest();
                    }
                });
            }

        });

        return entryReport;
    }

}
