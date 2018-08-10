package no.fint.linkwalker.exceptions;

public class InvalidTestUrlException extends FintLinkWalkerException {

    public InvalidTestUrlException(String message) {
        super(message);
    }

    public InvalidTestUrlException(String message, Throwable cause) {
        super(message, cause);
    }
}
