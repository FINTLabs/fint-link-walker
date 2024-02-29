package no.fint.linkwalker.service;

import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.fint.linkwalker.RelationFinder;
import no.fint.linkwalker.data.DiscoveredRelation;
import no.fint.linkwalker.data.TestedRelation;
import no.fint.linkwalker.dto.Status;
import no.fint.linkwalker.dto.TestCase;
import no.fint.linkwalker.dto.TestRequest;
import no.fint.linkwalker.exceptions.FintLinkWalkerException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestRunner {

    private final ResourceFetcher fetcher;

    @Async
    public void runTest(TestCase testCase) {
        String target = testCase.getTestRequest().getTarget();
        if (testCase.getStatus() == Status.RUNNING || testCase.getStatus() == Status.OK || testCase.getStatus() == Status.FAILED) {
            log.info("{}: {} has status {}, so it will not be run", testCase.getOrganisation(), target, testCase.getStatus());
            return;
        } else {
            log.info("{}: Running test {}", testCase.getOrganisation(), target);
        }

        testCase.start();
        runIt(testCase);
    }

    private void runIt(TestCase testCase) {
        TestRequest testRequest = testCase.getTestRequest();
        HttpHeaders headers = createHeaders();
        fetcher.fetchResource(testCase.getOrganisation(), testRequest.getClient(), testRequest.getTarget(), headers, String.class)
                .doOnError(throwable -> handleError(throwable, testCase, testRequest))
                .subscribe(response -> {
                    try {
                        if (JsonPath.parse(response.getBody()).read("$.total_items", int.class) == 0) {
                            testCase.partiallyFailed();
                            return;
                        }
                        harvestChildren(testCase, response.getBody());
                        long count = testCase.getRelations().values().stream().mapToLong(Collection::size).sum();
                        testCase.getRemaining().set(count);
                        log.info("{}: Found {} children.", testCase.getOrganisation(), count);
                        testChildren(testCase);
                        log.info("{}: Completed testing children.", testCase.getOrganisation());
                        long errors = testCase.getRelations().values().stream().flatMap(Collection::parallelStream).map(TestedRelation::getStatus).filter(status -> status == Status.FAILED).count();
                        log.info("{}: Found {} errors.", testCase.getOrganisation(), errors);
                        if (errors > 0)
                            testCase.failed(String.format("Found %d errors in children.", errors));
                        else
                            testCase.succeed();
                    } catch (FintLinkWalkerException e) {
                        testCase.failed(e);
                    }
                });
    }

    private void handleError(Throwable throwable, TestCase testCase, TestRequest testRequest) {
        if (throwable instanceof ResourceAccessException) {
            testCase.failed(String.format("An error occurred. Probably because the client you use don't have access to the resource. (%s)", throwable.getMessage()));
        } else if (throwable instanceof WebClientResponseException webClientResponseException) {
            int statusCode = webClientResponseException.getStatusCode().value();
            testCase.failed(String.format("Wrong status code. %d is not 200 OK. Response body: %s", statusCode, webClientResponseException.getResponseBodyAsString()));
            log.info("{}: Failing {} with status code {}", testCase.getOrganisation(), testRequest.getTarget(), statusCode);
        } else {
            log.info("{}: Failing {}", testCase.getOrganisation(), testRequest.getTarget());
            testCase.failed("An unexpected error occurred.");
        }
    }

    private void testChildren(TestCase testCase) {
        Map<String, Collection<TestedRelation>> allRelations = testCase.getRelations();
        allRelations.values().parallelStream().forEach(relations -> relations.parallelStream().forEach(relation -> testRelation(testCase, relation)));
    }

    private void testRelation(TestCase testCase, TestedRelation testedRelation) {
        HttpHeaders headers = createHeaders();
        Mono<ResponseEntity<Void>> monoResponse = fetcher.fetchResource(testCase.getOrganisation(), testCase.getTestRequest().getClient(), testedRelation.getUrl().toString(), headers, Void.class);
        monoResponse.subscribe(response -> {
            if (response.getStatusCode().is2xxSuccessful()) {
                testedRelation.setStatus(Status.OK);
            } else {
                testedRelation.setStatus(Status.FAILED);
                testedRelation.setReason(String.format("%s is not 200.", response.getStatusCode().value()));
            }
            testCase.getRemaining().decrementAndGet();
        });
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON_UTF8));

        return headers;
    }

    private void harvestChildren(TestCase parentCase, String entity) {
        Collection<DiscoveredRelation> relations = RelationFinder.findLinks(entity);
        relations.forEach(parentCase::addRelation);
    }
}
