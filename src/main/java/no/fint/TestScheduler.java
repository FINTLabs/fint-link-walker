package no.fint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;

@Service
public class TestScheduler {

    private Queue<TestCase> queuedTests = new LinkedList<>();

    @Autowired
    private TestRunner runner;
    @Autowired
    private TestCaseRepository repository;

    private static final Logger LOG = LoggerFactory.getLogger(TestScheduler.class);

    /**
     * queue up a test for running
     */
    public void scheduleTest(URL url) {
        TestCase testCase = repository.getCaseForURL(url);
        LOG.info("maybe scheduling {} with status {}", testCase.getTarget(), testCase.getStatus());
        if (testCase.getStatus() == Status.NOT_QUEUED) {
            LOG.info("Enqueuing {}", testCase.getTarget());
            testCase.enqueued();
            queuedTests.add(testCase);
        } else {
            LOG.info(".. not enqueing since status is {}", testCase.getStatus());
        }
    }

    /**
     * Pick out the first test in the queue and run it.
     */
    @Scheduled(fixedRate = 1000)
    public void runATest() {
        LOG.info("Emptying queue {} deep", queuedTests.size());
        while (!queuedTests.isEmpty()) {
            TestCase testCase = queuedTests.poll();
            try {
                LOG.info("Calling testrunner for test {}", testCase.getTarget());
                runner.runTest(testCase);
            } catch (IOException e) {
                LOG.info("Failing test {} with an IOException", testCase.getId());
                testCase.failed(e);
            }
        }
        LOG.info("queue empty");
    }

    public Collection<TestCase> allTests() {
        return repository.allTestCases();
    }
}
