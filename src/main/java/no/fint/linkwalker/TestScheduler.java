package no.fint.linkwalker;

import no.fint.linkwalker.dto.TestCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TestScheduler {

    @Autowired
    private TestRunner runner;
    @Autowired
    private TestCaseRepository repository;

    public TestCase scheduleTest(String url) {
        TestCase testCase = repository.buildNewCase(url);
        testCase.enqueued();
        runner.runTest(testCase);
        return testCase;
    }
}
