package no.fint;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * A TestCase gets the content of a URL, parses it, and follows any and all URLs
 * <p>
 * The TestCase is successful if the URL responds with 200 OK and all links contained in the response-object also pass their tests.
 */
@Slf4j
@Getter
public class TestCase {

    private final UUID id;
    private final URL target;
    private Status status;

    @CheckForNull
    private String reason;

    /*
        each relation has a name and a set of urls

        key is the relationship's name
     */
    private final Map<String, Collection<TestedRelation>> relations = new HashMap<>();

    public TestCase(UUID id, URL target) {
        this.id = id;
        this.target = target;
        status = Status.NOT_QUEUED;
    }

    private void transition(Status newStatus) {
        log.info("{} {} -> {}", target, status, newStatus);
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

    public void failed(IOException e) {
        transition(Status.FAILED);
        reason = e.getClass() + ": " + e.getMessage();
    }

    public void failed(String reason) {
        transition(Status.FAILED);
        this.reason = reason;
    }

    @CheckForNull
    public String getReason() {
        return reason;
    }

    public void addRelation(DiscoveredRelation discoveredRelation) {
        String rel = discoveredRelation.getRel();
        if (!relations.containsKey(rel)) {
            Set<TestedRelation> relationSet = new HashSet<>();
            relations.put(rel, relationSet);
        }
        Collection<TestedRelation> testedRelations = relations.get(rel);
        discoveredRelation.getLinks().forEach(link -> testedRelations.add(new TestedRelation(link, discoveredRelation.getPath())));
    }

    public Map<String, Collection<TestedRelation>> getRelations() {
        return relations;
    }

}
