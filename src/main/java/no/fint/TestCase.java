package no.fint;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.net.URL;
import java.util.UUID;

/**
 * A TestCase gets the content of a URL, parses it, and follows any and all URLs
 *
 * The TestCase is successful if the URL responds with 200 OK and all links contained in the response-object also pass their tests.
 */
public class TestCase {

    private final UUID id;
    private final URL target;
    private Status status;

    @CheckForNull
    private String reason;

    public TestCase(UUID id, URL target) {
        this.id = id;
        this.target = target;
        status = Status.NOT_STARTED;
    }

    public void start() {
        status = Status.RUNNING;
    }

    public URL getTarget() {
        return target;
    }

    public void failed(IOException e) {
        status = Status.FAILED;
        reason = e.getClass() + ": " + e.getMessage();
    }

    public void failed(String reason) {
        status = Status.FAILED;
        this.reason = reason;
    }

    public UUID getId() {
        return id;
    }

    public Status getStatus() {
        return status;
    }

    @CheckForNull
    public String getReason() {
        return reason;
    }
}
