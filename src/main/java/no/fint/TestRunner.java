package no.fint;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
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
        HttpGet get = new HttpGet(testCase.getTarget().toString());

        client.execute(get, response -> {
            if (response.getStatusLine().getStatusCode() == 200) {
                // 200 OK is test succeed, regardless of what the content is, right?
                testCase.succeed();
                harvestChildren(testCase, response);
                testChildren(testCase, client);
            } else {
                LOG.info("Failing {}", testCase.getTarget());
                testCase.failed("Wrong status code. " + response.getStatusLine().getStatusCode() + " is not 200 OK");
            }
            return "ok fine, whatever.";
        });
    }

    private void testChildren(TestCase testCase, CloseableHttpClient client) {
        Map<String, Collection<TestedRelation>> allRelations = testCase.getRelations();
        allRelations.forEach((relationName, relations) -> {
            relations.forEach(relation -> this.testRelation(relation, client));
        });
    }

    private void testRelation(TestedRelation testedRelation, CloseableHttpClient client) {
        HttpGet get = new HttpGet(testedRelation.getUrl().toString());

        try {
            client.execute(get, response -> {
                if (response.getStatusLine().getStatusCode() == 200) {
                    testedRelation.setStatus(Status.OK);
                } else {
                    testedRelation.setStatus(Status.FAILED);
                    testedRelation.setReason(response.getStatusLine().getStatusCode() + " is not 200.");
                }
                return "";
            });
        } catch (IOException e) {
            LOG.warn("Failed to test relation {} => {}:{}", testedRelation.getUrl(), e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void harvestChildren(TestCase parentCase, HttpResponse response) throws IOException {
        InputStream contentStream = response.getEntity().getContent();
        Collection<DiscoveredRelation> relations = RelationFinder.findLinks(contentStream);
        relations.stream().forEach(parentCase::addRelation);
    }
}
