package no.fint.linkwalker;

import lombok.Data;
import no.fint.linkwalker.dto.Status;

import java.net.URL;

/**
 * TestedRelation is a single link with a named relation that has been or is about to be tested.
 */
@Data
public class TestedRelation {

    private final URL url;
    /**
     * RUNNING, OK, FAILED
     */
    private Status status;

    /**
     * Any reason we could guess that this rel failed
     */
    private String reason;

    /**
     * The path in the original document where this rel was discovered
     */
    private final String path;

    public TestedRelation(URL url, String path) {
        this.status = Status.RUNNING;
        this.url = url;
        this.path = path;
    }
}
