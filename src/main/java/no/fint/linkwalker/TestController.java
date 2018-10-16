package no.fint.linkwalker;

import com.fasterxml.jackson.annotation.JsonView;
import lombok.extern.slf4j.Slf4j;
import no.fint.linkwalker.dto.Status;
import no.fint.linkwalker.dto.TestCase;
import no.fint.linkwalker.dto.TestCaseViews;
import no.fint.linkwalker.dto.TestRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;

import java.util.Collection;
import java.util.UUID;

@Slf4j
@RestController
@CrossOrigin
@RequestMapping("/api/tests/links/{organisation}")
public class TestController {

    @Autowired
    private TestScheduler testScheduler;

    @Autowired
    private TestCaseRepository repository;

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
}
