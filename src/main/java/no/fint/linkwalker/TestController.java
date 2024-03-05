package no.fint.linkwalker;

import com.fasterxml.jackson.annotation.JsonView;
import lombok.extern.slf4j.Slf4j;
import no.fint.linkwalker.dto.Status;
import no.fint.linkwalker.dto.TestCase;
import no.fint.linkwalker.dto.TestCaseViews;
import no.fint.linkwalker.dto.TestRequest;
import org.apache.poi.util.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@RestController
@CrossOrigin
@RequestMapping("/{organisation}/links")
public class TestController {

    @Autowired
    private TestScheduler testScheduler;

    @Autowired
    private TestCaseRepository repository;

    @Autowired
    private ReportService reportService;

    /**
     * Kicks off a startTest of an endpoint. All testing is async
     *
     * @param testRequest Contains the baseUrl and endpoint under test and
     * @return a UUID that can be used to retrieve the current status of a running startTest
     */
    @PostMapping
    public ResponseEntity<TestCase> startTest(@PathVariable String organisation, @RequestBody TestRequest testRequest) {
        TestCase testCase = testScheduler.scheduleTest(organisation, testRequest);
        log.info("Registering testcase " + testCase.getId());
        UriComponents uriComponents = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(testCase.getId());
        return ResponseEntity.created(uriComponents.toUri()).body(testCase);
    }

    @JsonView(TestCaseViews.ResultsOverview.class)
    @GetMapping
    public Collection<TestCase> getAllTests(@PathVariable String organisation) {
        return repository.allTestCases(organisation);
    }

    @PutMapping
    public ResponseEntity clearAllTests(@PathVariable String organisation) {
        repository.clearTests(organisation);
        return ResponseEntity.ok().build();
    }

    @JsonView(TestCaseViews.Details.class)
    @GetMapping("/{id}")
    public TestCase getTest(@PathVariable String organisation,
                            @PathVariable UUID id,
                            @RequestParam(required = false) String status) {
        TestCase testCase = repository.getCaseForId(organisation, id);
        if (StringUtils.isEmpty(status)) {
            return testCase;
        } else {
            return testCase.filterAndCopyRelations(Status.get(status));
        }
    }

    @GetMapping(value = "/{id}/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Resource> downloadTest(@PathVariable String organisation,
                                                 @PathVariable UUID id,
                                                 @RequestParam(required = false) String status) throws IOException {

        TestCase testCase = repository.getCaseForId(organisation, id);

        LocalDateTime ldt = LocalDateTime.now();
        String formattedDate = DateTimeFormatter.ofPattern("ddMMyyyy_HHmmss", Locale.ENGLISH).format(ldt);

        String fileName = String.format("%s-report-%s.xlsx", organisation, formattedDate);
        ByteArrayInputStream export = reportService.export(testCase);
        ByteArrayResource out = new ByteArrayResource(IOUtils.toByteArray(export));

        return ResponseEntity
                .ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                .contentLength(out.contentLength())
        .body(out);
    }


}
