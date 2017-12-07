package no.fint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class TestCaseRepository {

    private static final Logger LOG = LoggerFactory.getLogger(TestCaseRepository.class);

    private final Map<URL, TestCase> cases = new ConcurrentHashMap<>();

    /**
     * Gets existing, or creates new testcase for a particular URL
     *
     * @param url
     * @return
     */
    public TestCase getCaseForURL(URL url) {
        if (!cases.containsKey(url)) {
            TestCase testCase = new TestCase(UUID.randomUUID(), url);
            cases.put(url, testCase);
        }
        return cases.get(url);
    }

    public Collection<TestCase> allTestCases() {
        return cases.values();
    }
}
