package no.fint.linkwalker;

import no.fint.linkwalker.dto.TestCase;
import no.fint.linkwalker.exceptions.NoSuchTestCaseException;
import org.springframework.stereotype.Repository;

import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class TestCaseRepository {

    private final Map<UUID, TestCase> cases = new ConcurrentHashMap<>();

    public TestCase buildNewCase(String url) {
        TestCase testCase = new TestCase(UUID.randomUUID(), url);
        cases.put(testCase.getId(), testCase);
        return testCase;
    }

    public Collection<TestCase> allTestCases() {
        return cases.values();
    }

    public TestCase getCaseForId(UUID id) {
        if (cases.containsKey(id)) {
            return cases.get(id);
        } else {
            throw new NoSuchTestCaseException(id);
        }
    }
}
