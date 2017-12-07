package no.fint;

import javax.annotation.CheckForNull;
import java.net.URL;

/**
 * TestedRelation is a single link with a named relation that has been or is about to be tested.
 */
public class TestedRelation {

    private final URL url;
    /**
     * OK or FAILED
     */
    private Status status;

    /**
     * Any reason we could guess that this rel failed
     */
    @CheckForNull
    private String reason;

    /**
     * The path in the original document where this rel was discovered
     */
    private final String path;

    public TestedRelation(URL url, String path) {
        this.url = url;
        this.path = path;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Status getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public URL getUrl() {
        return url;
    }
}
