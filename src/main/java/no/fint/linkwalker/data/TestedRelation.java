package no.fint.linkwalker.data;

import lombok.Data;
import no.fint.linkwalker.dto.Status;

import java.net.URL;

@Data
public class TestedRelation {

    private final URL url;
    private final URL parentUrl;
    private Status status;
    private String reason;

    public TestedRelation(URL url, URL parentUrl) {
        this.status = Status.RUNNING;
        this.url = url;
        this.parentUrl = parentUrl;
    }
}
