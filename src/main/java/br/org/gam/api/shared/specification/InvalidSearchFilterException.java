package br.org.gam.api.shared.specification;

import java.util.Map;

public class InvalidSearchFilterException extends RuntimeException {

    private final Map<String, Object> details;

    public InvalidSearchFilterException(String message) {
        this(message, Map.of(), null);
    }

    public InvalidSearchFilterException(String message, Throwable cause) {
        this(message, Map.of(), cause);
    }

    public InvalidSearchFilterException(String message, Map<String, Object> details) {
        this(message, details, null);
    }

    public InvalidSearchFilterException(String message, Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.details = Map.copyOf(details);
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
