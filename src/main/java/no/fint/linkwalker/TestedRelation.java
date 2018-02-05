package no.fint.linkwalker;

import lombok.Data;
import no.fint.linkwalker.dto.Status;

import java.net.URL;

@Data
public class TestedRelation {

    private final URL url;
    private Status status;
    private String reason;

    public TestedRelation(URL url) {
        this.status = Status.RUNNING;
        this.url = url;
    }
}
