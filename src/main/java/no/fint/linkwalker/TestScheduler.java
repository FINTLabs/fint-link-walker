package no.fint.linkwalker;

import no.fint.linkwalker.dto.TestCase;
import no.fint.linkwalker.dto.TestRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TestScheduler {

    @Autowired
    private TestRunner runner;
    @Autowired
    private TestCaseRepository repository;

    public TestCase scheduleTest(TestRequest testRequest) {
        TestCase testCase = repository.buildNewCase(testRequest);
        testCase.enqueued();
        runner.runTest(testCase);
        return testCase;
    }
}
