package no.fintlabs.linkwalker.task.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Data
public class Report {

    private Task.Status status;
    private String id;
    private int relationErrors;
    private int healthyRelations;
    private final List<EntryReport> entryReports = new ArrayList<>();

    public void incrementRelationErrors(int count) {
        relationErrors += count;
    }

    public void incrementHealthyRelations(int count) {
        healthyRelations += count;
    }

    public static Collection<Report> ofTasks(Optional<Collection<Task>> optionalTasks) {
        if (optionalTasks.isEmpty()) {
            return List.of();
        }

        List<Report> reports = new ArrayList<>();
        optionalTasks.get().forEach(task -> reports.add(ofTask(task)));
        return reports;
    }

    public static Report ofTask(Task task) {
        Report report = new Report();
        report.setStatus(task.getStatus());
        report.setId(task.getId());

        task.getEntryReports().forEach(taskEntryReport -> {
            int relationErrors = taskEntryReport.getRelationErrors().size();
            report.incrementHealthyRelations(taskEntryReport.getRelationLinks().size() - relationErrors);

            if (taskEntryReport.hasRelationError()) {
                report.getEntryReports().add(taskEntryReport);
                report.incrementRelationErrors(relationErrors);
            }
        });

        return report;
    }

}
