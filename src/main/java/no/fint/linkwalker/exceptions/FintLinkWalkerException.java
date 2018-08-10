package no.fint.linkwalker.exceptions;

public class FintLinkWalkerException extends RuntimeException {

    public FintLinkWalkerException(String message) {
        super(message);
    }

    public FintLinkWalkerException(String message, Throwable cause) {
        super(message, cause);
    }
}
