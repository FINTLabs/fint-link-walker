package no.fint.linkwalker;

import no.fint.linkwalker.dto.TestCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.Queue;

@Service
public class TestScheduler {

    private Queue<TestCase> queuedTests = new LinkedList<>();

    @Autowired
    private TestRunner runner;
    @Autowired
    private TestCaseRepository repository;

    /**
     * queue up a test for running
     */
    public TestCase scheduleTest(String url) {
        TestCase testCase = repository.buildNewCase(url);
        testCase.enqueued();
        queuedTests.add(testCase);
        return testCase;
    }

    /**
     * Pick out the first test in the queue and run it.
     */
    @Scheduled(fixedRate = 1000)
    public void runATest() {
        while (!queuedTests.isEmpty()) {
            TestCase testCase = queuedTests.poll();
            runner.runTest(testCase);
        }
    }
}
