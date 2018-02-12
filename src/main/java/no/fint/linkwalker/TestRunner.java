package no.fint.linkwalker;

import lombok.extern.slf4j.Slf4j;
import no.fint.linkwalker.dto.Status;
import no.fint.linkwalker.dto.TestCase;
import no.fint.linkwalker.dto.TestRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.Map;

@Slf4j
@Service
public class TestRunner {
    @Autowired
    private RestTemplate restTemplate;

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
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-org-id", testRequest.getOrgId());
        headers.set("x-client", testRequest.getClient());

        ResponseEntity<String> response = restTemplate.exchange(testRequest.getTarget(), HttpMethod.GET, new HttpEntity<>("parameters", headers), String.class);
        if (response.getStatusCode().is2xxSuccessful()) {
            testCase.succeed();
            harvestChildren(testCase, response.getBody());
            testChildren(testCase);
        } else {
            log.info("Failing {}", testRequest.getTarget());
            testCase.failed(String.format("Wrong status code. %s is not 200 OK", response.getStatusCode().value()));
        }
    }

    private void testChildren(TestCase testCase) {
        Map<String, Collection<TestedRelation>> allRelations = testCase.getRelations();
        allRelations.forEach((relationName, relations) -> relations.forEach(relation -> testRelation(testCase, relation)));
    }

    private void testRelation(TestCase testCase, TestedRelation testedRelation) {
        TestRequest testRequest = testCase.getTestRequest();
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-org-id", testRequest.getOrgId());
        headers.set("x-client", testRequest.getClient());

        ResponseEntity<Void> response = restTemplate.exchange(testedRelation.getUrl().toString(), HttpMethod.GET, new HttpEntity<>("parameters", headers), Void.class);
        if (response.getStatusCode().is2xxSuccessful()) {
            testedRelation.setStatus(Status.OK);
        } else {
            testedRelation.setStatus(Status.FAILED);
            testedRelation.setReason(String.format("%s is not 200.", response.getStatusCode().value()));
        }
    }

    private void harvestChildren(TestCase parentCase, String entity) {
        Collection<DiscoveredRelation> relations = RelationFinder.findLinks(entity);
        relations.forEach(parentCase::addRelation);
    }
}
