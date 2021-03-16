package no.fint.linkwalker;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import no.fint.linkwalker.dto.TestCase;
import no.fint.linkwalker.dto.TestRequest;
import no.fint.linkwalker.exceptions.NoSuchTestCaseException;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

@Repository
public class TestCaseRepository {

    private final Multimap<String, TestCase> cases = Multimaps.synchronizedListMultimap(ArrayListMultimap.create());

    public TestCase buildNewCase(String organisation, TestRequest testRequest) {
        TestCase testCase = new TestCase(organisation, testRequest);
        cases.put(organisation, testCase);
        return testCase;
    }

    public Collection<TestCase> allTestCases(String organisation) {
        return cases.get(organisation);
    }

    public void clearTests(String organisation) {
        cases.get(organisation).clear();
    }

    public TestCase getCaseForId(String organisation, UUID id) {
        if (!cases.containsKey(organisation))
            throw new NoSuchTestCaseException(id);

        return cases
                .get(organisation)
                .stream()
                .filter(i -> i.getId().equals(id))
                .findAny()
                .orElseThrow(() -> new NoSuchTestCaseException(id));
    }
}
