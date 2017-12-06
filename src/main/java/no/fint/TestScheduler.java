package no.fint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TestScheduler {

    private Map<UUID, TestCase> caseRepo = new HashMap<UUID, TestCase>();
    private Queue<TestCase> queuedTests = new LinkedList<>();
    private Collection<URL> alreadyTestedURLs = new ArrayList<>();

    @Autowired
    private TestRunner runner;

    private static final Logger LOG = LoggerFactory.getLogger(TestScheduler.class);

    /**
     * queue up a test for running
     * @param testCase
     */
    public void scheduleTest(TestCase testCase) {
        LOG.info("Scheduling test " + testCase.getId());
        if (alreadyTestedURLs.contains(testCase.getTarget())) {
            LOG.info("... but " + testCase.getTarget() + " is already scheduled and/or tested.");
        } else {
            caseRepo.put(testCase.getId(), testCase);
            queuedTests.add(testCase);
            alreadyTestedURLs.add(testCase.getTarget());
        }
    }

    /**
     * search for a given test, using its identity
     * @param uuid
     * @return
     */
    public Optional<TestCase> findTestCase(UUID uuid) {
        return Optional.ofNullable(caseRepo.get(uuid));
    }

    /**
     * Pick out the first test in the queue and run it.
     */
    @Scheduled(fixedRate = 1000)
    public void runATest() {
        if (!queuedTests.isEmpty()) {
            TestCase testCase = queuedTests.poll();
            try {
                LOG.info("Running test " + testCase.getId());
                runner.runTest(testCase);
            } catch (IOException e) {
                LOG.info("Failing test " + testCase.getId() + " with an IOException");
                testCase.failed(e);
            }
        }
    }

    public Collection<TestCase> allTests() {
        return caseRepo.values();
    }
}
