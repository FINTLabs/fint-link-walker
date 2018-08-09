package no.fint.linkwalker.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class NoSuchTestCaseException extends FintLinkWalkerException {
    public NoSuchTestCaseException(UUID id) {
        super(String.format("No test case with id %s can be found.", id.toString()));
    }
}
