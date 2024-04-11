package no.fintlabs.linkwalker.report;

import lombok.RequiredArgsConstructor;
import no.fintlabs.linkwalker.report.model.Report;
import no.fintlabs.linkwalker.task.TaskCache;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequestMapping("/report/{organization}")
@RequiredArgsConstructor
public class ReportController {

    private final TaskCache taskCache;

    @GetMapping
    public ResponseEntity<Collection<Report>> getReports(@PathVariable String organization) {
        return ResponseEntity.ok(Report.ofTasks(taskCache.getAll(organization)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Report> getReport(@PathVariable String organization, @PathVariable String id) {
        return taskCache.exists(organization, id)
                ? ResponseEntity.ok(Report.ofTask(taskCache.get(organization, id).get()))
                : ResponseEntity.notFound().build();
    }

}
