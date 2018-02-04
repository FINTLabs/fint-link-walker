package no.fint.linkwalker;

import lombok.extern.slf4j.Slf4j;
import no.fint.linkwalker.dto.TestCase;
import no.fint.linkwalker.dto.TestRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;

import java.util.Collection;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/tests")
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
    public ResponseEntity<TestCase> startTest(@RequestBody TestRequest testRequest) {
        TestCase testCase = testScheduler.scheduleTest(testRequest.getTarget());
        log.info("Registering testcase " + testCase.getId());
        UriComponents uriComponents = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(testCase.getId());
        return ResponseEntity.created(uriComponents.toUri()).body(testCase);
    }

    @GetMapping
    public Collection<TestCase> getAllTests() {
        return repository.allTestCases();
    }

    @GetMapping("/{id}")
    public TestCase getTest(@PathVariable UUID id) {
        return repository.getCaseForId(id);
    }
}
