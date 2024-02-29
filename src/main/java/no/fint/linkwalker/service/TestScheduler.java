package no.fint.linkwalker.service;

import lombok.RequiredArgsConstructor;
import no.fint.linkwalker.TestCaseRepository;
import no.fint.linkwalker.dto.TestCase;
import no.fint.linkwalker.dto.TestRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TestScheduler {

    private final TestRunner runner;
    private final TestCaseRepository repository;

    public TestCase scheduleTest(String organisation, TestRequest testRequest) {
        TestCase testCase = repository.buildNewCase(organisation, testRequest);
        testCase.enqueued();
        runner.runTest(testCase);
        return testCase;
    }
}
