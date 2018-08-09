package no.fint.linkwalker.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import no.fint.linkwalker.DiscoveredRelation;
import no.fint.linkwalker.TestedRelation;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A TestCase gets the content of a URL, parses it, and follows any and all URLs
 * <p>
 * The TestCase is successful if the URL responds with 200 OK and all links contained in the response-object also pass their tests.
 */
@Slf4j
@Getter
public class TestCase {

    private final UUID id;
    private final AtomicLong remaining = new AtomicLong();
    private final Map<String, Collection<TestedRelation>> relations = new HashMap<>();
    private Status status;
    private String reason;
    private TestRequest testRequest;
    @JsonFormat(pattern = "dd.MM.yyyy HH:mm:ss")
    private Date time;

    public TestCase(TestRequest testRequest) {
        this.id = UUID.randomUUID();
        this.testRequest = testRequest;
        this.time = new Date();
        status = Status.NOT_QUEUED;
    }

    private void transition(Status newStatus) {
        log.info("{} {} -> {}", testRequest.getTarget(), status, newStatus);
        this.status = newStatus;
    }

    public void start() {
        transition(Status.RUNNING);
    }

    public void enqueued() {
        transition(Status.NOT_STARTED);
    }

    public void succeed() {
        transition(Status.OK);
    }

    public void failed(Throwable t) {
        transition(Status.FAILED);
        reason = t.getClass() + ": " + t.getMessage();
    }

    public void failed(String reason) {
        transition(Status.FAILED);
        this.reason = reason;
    }

    public void addRelation(DiscoveredRelation discoveredRelation) {
        String rel = discoveredRelation.getRel();
        if (!relations.containsKey(rel)) {
            Set<TestedRelation> relationSet = new HashSet<>();
            relations.put(rel, relationSet);
        }
        Collection<TestedRelation> testedRelations = relations.get(rel);
        discoveredRelation.getLinks().forEach(link -> testedRelations.add(new TestedRelation(link)));
    }

    public Map<String, Collection<TestedRelation>> getRelations() {
        return relations;
    }

}
