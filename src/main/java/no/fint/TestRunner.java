package no.fint;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TestRunner {
    @Autowired
    TestScheduler scheduler;

    @Async
    public void runTest(TestCase testCase) throws IOException {
        testCase.start();

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(testCase.getTarget().toString());
            client.execute(get, response -> {
                if (response.getStatusLine().getStatusCode() == 200) {
                    InputStream contentStream = response.getEntity().getContent();
                    Collection<RelationFinder.Relation> relations = RelationFinder.findLinks(contentStream);
                    // for each relation, create a new test-case and register it with this test-case
                    List<TestCase> newTestCases = relations.stream()
                            .flatMap(rel -> rel.getLinks().stream())
                            .map(link -> new TestCase(UUID.randomUUID(), link))
                            .collect(Collectors.toList());
                    newTestCases.stream().forEach(scheduler::scheduleTest);

                    // ok, so now I've kicked off, recursively, all the tests. how to manage the results?


                } else {
                    testCase.failed("Wrong status code. " + response.getStatusLine().getStatusCode() + " is not 200 OK");
                }
                return "ok fine, whatever.";
            });
        }
    }
}
