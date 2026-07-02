package br.org.gam.api.shared.specification;

public class InvalidSearchFilterException extends RuntimeException {

    public InvalidSearchFilterException(String message) {
        super(message);
    }

    public InvalidSearchFilterException(String message, Throwable cause) {
        super(message, cause);
    }
}
