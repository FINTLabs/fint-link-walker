package no.fint.linkwalker;

import com.fasterxml.jackson.annotation.JsonView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.fint.linkwalker.dto.Status;
import no.fint.linkwalker.dto.TestCase;
import no.fint.linkwalker.dto.TestCaseViews;
import no.fint.linkwalker.dto.TestRequest;
import no.fint.linkwalker.service.ReportService;
import no.fint.linkwalker.service.TestScheduler;
import org.apache.poi.util.IOUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@RestController
@CrossOrigin
@RequiredArgsConstructor
@RequestMapping("/api/tests/{organisation}/links")
public class RelationTestController {

    private final TestScheduler testScheduler;
    private final TestCaseRepository repository;
    private final ReportService reportService;

    /**
     * Kicks off a startTest of an endpoint. All testing is async
     *
     * @param testRequest Contains the baseUrl and endpoint under test and
     * @return a UUID that can be used to retrieve the current status of a running startTest
     */
    @PostMapping
    public ResponseEntity<TestCase> startTest(ServerWebExchange serverWebExchange, @PathVariable String organisation, @RequestBody TestRequest testRequest) {
        TestCase testCase = testScheduler.scheduleTest(organisation, testRequest);
        log.info("Registering testcase " + testCase.getId());
        return ResponseEntity.created(URI.create(serverWebExchange.getRequest().getURI().toASCIIString() + "/{id}")).body(testCase);
    }

    @JsonView(TestCaseViews.ResultsOverview.class)
    @GetMapping
    public Collection<TestCase> getAllTests(@PathVariable String organisation) {
        return repository.allTestCases(organisation);
    }

    @PostMapping("/{dn}")
    public ResponseEntity<Object> test(ServerWebExchange exchange, @PathVariable String dn) {
        return ResponseEntity.ok().build();
    }

    @PutMapping
    public ResponseEntity<Void> clearAllTests(@PathVariable String organisation) {
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
