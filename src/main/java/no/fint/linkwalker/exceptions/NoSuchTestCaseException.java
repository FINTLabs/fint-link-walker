package no.fint.linkwalker.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class NoSuchTestCaseException extends RuntimeException {
    public NoSuchTestCaseException(UUID id) {
        super("No test case with id " + id + " can be found.");
    }
}
