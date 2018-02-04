package no.fint.linkwalker;

import no.fint.linkwalker.dto.Status;
import no.fint.linkwalker.dto.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

@Service
public class TestRunner {
    private static final Logger LOG = LoggerFactory.getLogger(TestRunner.class);

    @Autowired
    private RestTemplate restTemplate;

    @Async
    public void runTest(TestCase testCase) {
        if (testCase.getStatus() == Status.RUNNING || testCase.getStatus() == Status.OK || testCase.getStatus() == Status.FAILED) {
            LOG.info("{} has status {}, so it will not be run", testCase.getTarget(), testCase.getStatus());
            return;
        } else {
            LOG.info("Running test {}", testCase.getTarget());
        }

        testCase.start();
        runIt(testCase);
    }

    private void runIt(TestCase testCase) {
        ResponseEntity<String> response = restTemplate.getForEntity(testCase.getTarget(), String.class);
        if (response.getStatusCode().is2xxSuccessful()) {
            testCase.succeed();
            harvestChildren(testCase, response.getBody());
            testChildren(testCase);
        } else {
            LOG.info("Failing {}", testCase.getTarget());
            testCase.failed(String.format("Wrong status code. %s is not 200 OK", response.getStatusCode().value()));
        }
    }

    private void testChildren(TestCase testCase) {
        Map<String, Collection<TestedRelation>> allRelations = testCase.getRelations();
        allRelations.forEach((relationName, relations) -> {
            relations.forEach(this::testRelation);
        });
    }

    private void testRelation(TestedRelation testedRelation) {
        ResponseEntity<Void> response = restTemplate.getForEntity(testedRelation.getUrl().toString(), Void.class);
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
