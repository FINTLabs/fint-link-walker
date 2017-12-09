package no.fint;

import org.apache.http.HttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

@Service
public class TestRunner {
    private static final Logger LOG = LoggerFactory.getLogger(TestRunner.class);

    @Autowired
    TestScheduler scheduler;

    @Autowired
    TestCaseRepository repository;

    @Autowired
    HttpClient httpClient;

    @Async
    public void runTest(TestCase testCase) throws IOException {
        if (testCase.getStatus() == Status.RUNNING || testCase.getStatus() == Status.OK || testCase.getStatus() == Status.FAILED) {
            LOG.info("{} has status {}, so it will not be run", testCase.getTarget(), testCase.getStatus());
            return;
        } else {
            LOG.info("Running test {}", testCase.getTarget());
        }

        testCase.start();

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            runIt(testCase, client);
        }
    }

    private void runIt(TestCase testCase, CloseableHttpClient client) throws IOException {
        HttpClient.Response testResponse = httpClient.get(testCase.getTarget());
        if (testResponse.getResponseCode() == 200) {
            testCase.succeed();
            harvestChildren(testCase, testResponse.getEntity());
            testChildren(testCase);
        } else {
            LOG.info("Failing {}", testCase.getTarget());
            testCase.failed("Wrong status code. " + testResponse.getResponseCode() + " is not 200 OK");
        }
    }

    private void testChildren(TestCase testCase) {
        Map<String, Collection<TestedRelation>> allRelations = testCase.getRelations();
        allRelations.forEach((relationName, relations) -> {
            relations.forEach(relation -> this.testRelation(relation));
        });
    }

    private void testRelation(TestedRelation testedRelation) {
        HttpClient.Response testResponse = httpClient.get(testedRelation.getUrl());
        if (testResponse.getResponseCode() == 200) {
            testedRelation.setStatus(Status.OK);
        } else {
            testedRelation.setStatus(Status.FAILED);
            testedRelation.setReason(testResponse.getResponseCode() + " is not 200.");
        }
    }

    private void harvestChildren(TestCase parentCase, String entity) {
        Collection<DiscoveredRelation> relations = RelationFinder.findLinks(entity);
        relations.stream().forEach(parentCase::addRelation);
    }
}
