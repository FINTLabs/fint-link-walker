package no.fint;

import org.hibernate.validator.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotNull;
import javax.websocket.server.PathParam;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/")
public class TestController {

    private static final Logger LOG = LoggerFactory.getLogger(TestController.class);

    private URL defaultURL = new URL("https://play-with-fint.felleskomponent.no/");

    @Autowired
    private TestScheduler testScheduler;

    @Autowired
    private TestCaseRepository repository;

    public TestController() throws MalformedURLException {
    }

    /**
     * Kicks off a startTest of an endpoint. All testing is async
     *
     * @param endpoint The endpoint under startTest
     * @param baseURL  - optional, lets the end-user startTest different environments (so long as they are available)
     * @return a UUID that can be used to retrieve the current status of a running startTest
     */
    @PostMapping
    public TestCase startTest(@RequestParam("endpoint") @NotBlank String endpoint, @RequestParam(value = "base", required = false) URL baseURL) throws MalformedURLException {
        TestCase testCase = testScheduler.scheduleTest(buildUrl(endpoint, baseURL));
        LOG.info("Registering testcase " + testCase.getId());
        return testCase;
    }

    @GetMapping("/")
    public Collection<TestCase> getAllTests() {
        return repository.allTestCases();
    }

    @GetMapping("/search")
    public TestCase getTest(@RequestParam("id") @NotNull UUID id) {
        return repository.getCaseForId(id);
    }

    private URL buildUrl(String endpoint, URL baseURL) throws MalformedURLException {
        if (baseURL != null) {
            return new URL(baseURL, endpoint);
        } else {
            return new URL(defaultURL, endpoint);
        }
    }
}
