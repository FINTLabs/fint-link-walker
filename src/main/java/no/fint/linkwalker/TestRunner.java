package no.fint.linkwalker;

import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;
import no.fint.linkwalker.dto.Status;
import no.fint.linkwalker.dto.TestCase;
import no.fint.linkwalker.dto.TestRequest;
import no.fint.linkwalker.exceptions.FintLinkWalkerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@Slf4j
@Service
public class TestRunner {

    @Autowired
    private ResourceFetcher fetcher;

    @Async
    public void runTest(TestCase testCase) {
        String target = testCase.getTestRequest().getTarget();
        if (testCase.getStatus() == Status.RUNNING || testCase.getStatus() == Status.OK || testCase.getStatus() == Status.FAILED) {
            log.info("{} has status {}, so it will not be run", target, testCase.getStatus());
            return;
        } else {
            log.info("Running test {}", target);
        }

        testCase.start();
        runIt(testCase);
    }

    private void runIt(TestCase testCase) {
        TestRequest testRequest = testCase.getTestRequest();
        HttpHeaders headers = createHeaders();

        try {
            ResponseEntity<String> response = fetcher.fetch(testRequest.getClient(), testRequest.getTarget(), headers, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                try {
                    if (JsonPath.parse(response.getBody()).read("$.total_items", int.class) == 0) {
                        testCase.partiallyFailed();
                        return;
                    }
                    harvestChildren(testCase, response.getBody());
                    long count = testCase.getRelations().values().stream().mapToLong(Collection::size).sum();
                    testCase.getRemaining().set(count);
                    log.info("Found {} children.", count);
                    testChildren(testCase);
                    log.info("Completed testing children.");
                    long errors = testCase.getRelations().values().stream().flatMap(Collection::parallelStream).map(TestedRelation::getStatus).filter(status -> status == Status.FAILED).count();
                    log.info("Found {} errors.", errors);
                    if (errors > 0)
                        testCase.failed(String.format("Found %d errors in children.", errors));
                    else
                        testCase.succeed();
                } catch (FintLinkWalkerException e) {
                    testCase.failed(e);
                }
            } else {
                log.info("Failing {}", testRequest.getTarget());
                testCase.failed(String.format("Wrong status code. %s is not 200 OK", response.getStatusCode().value()));
            }
        } catch (ResourceAccessException e) {
            testCase.failed(String.format("An error occurred. Probably because the client you use don't have access to the resource. (%s) ", e.getMessage()));
        }
    }

    private void testChildren(TestCase testCase) {
        Map<String, Collection<TestedRelation>> allRelations = testCase.getRelations();
        allRelations.values().parallelStream().forEach(relations -> relations.parallelStream().forEach(relation -> testRelation(testCase, relation)));
    }

    private void testRelation(TestCase testCase, TestedRelation testedRelation) {
        HttpHeaders headers = createHeaders();


        ResponseEntity<Void> response = fetcher.fetch(testCase.getTestRequest().getClient(), testedRelation.getUrl().toString(), headers, Void.class);
        if (response.getStatusCode().is2xxSuccessful()) {
            testedRelation.setStatus(Status.OK);
        } else {
            testedRelation.setStatus(Status.FAILED);
            testedRelation.setReason(String.format("%s is not 200.", response.getStatusCode().value()));
        }
        testCase.getRemaining().decrementAndGet();
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON_UTF8));

        return headers;
    }

    private void harvestChildren(TestCase parentCase, String entity) {
        Collection<DiscoveredRelation> relations = RelationFinder.findLinks(entity);
        relations.forEach(parentCase::addRelation);
    }
}
