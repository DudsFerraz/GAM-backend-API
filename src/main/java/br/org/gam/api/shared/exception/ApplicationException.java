package br.org.gam.api.shared.exception;

import java.util.Map;
import lombok.Getter;

@Getter
public abstract class ApplicationException extends RuntimeException {

    private final String code;
    private final String resource;
    private final Object identifier;
    private final Map<String, Object> details;

    protected ApplicationException(String code, String message, String resource, Object identifier) {
        this(code, message, resource, identifier, Map.of());
    }

    protected ApplicationException(String code, String message, String resource, Object identifier,
                                   Map<String, Object> details) {
        super(message);
        this.code = code;
        this.resource = resource;
        this.identifier = identifier;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

}
