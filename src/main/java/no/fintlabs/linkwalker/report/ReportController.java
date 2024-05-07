package no.fintlabs.linkwalker.report;

import lombok.RequiredArgsConstructor;
import no.fintlabs.linkwalker.report.model.Report;
import no.fintlabs.linkwalker.Task;
import no.fintlabs.linkwalker.TaskCache;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.Optional;

@RestController
@RequestMapping("/report/{organization}")
@RequiredArgsConstructor
public class ReportController {

    private final TaskCache taskCache;
    private final ReportService reportService;

    @GetMapping
    public ResponseEntity<Collection<Report>> getReports(@PathVariable String organization) {
        return ResponseEntity.ok(Report.ofTasks(taskCache.getAll(organization)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Report> getReport(@PathVariable String organization, @PathVariable String id) {
        return taskCache.get(organization, id)
                .map(task -> ResponseEntity.ok(Report.ofTask(task)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadReport(@PathVariable String organization, @PathVariable String id) {
        Optional<Task> optionalTask = taskCache.get(organization, id);

        if (optionalTask.isPresent()) {
            byte[] spreadsheet = reportService.createSpreadSheet(optionalTask.get());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "relasjonstest.xlsx");

            return new ResponseEntity<>(spreadsheet, headers, HttpStatus.OK);
        }

        return ResponseEntity.notFound().build();
    }

}
