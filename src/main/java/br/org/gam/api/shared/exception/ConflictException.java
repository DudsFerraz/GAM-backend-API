package br.org.gam.api.shared.exception;

import java.util.Map;

public class ConflictException extends ApplicationException {

    private ConflictException(String code, String message, String resource, Object identifier) {
        super(code, message, resource, identifier);
    }

    private ConflictException(String code, String message, String resource, Object identifier,
                              Map<String, Object> details) {
        super(code, message, resource, identifier, details);
    }

    public static ConflictException resource(String resource, Object identifier, String message) {
        return new ConflictException("RESOURCE_CONFLICT", message, resource, identifier);
    }

    public static ConflictException resource(String code, String resource, Object identifier, String message) {
        return new ConflictException(code, message, resource, identifier);
    }

    public static ConflictException resource(String code, String resource, Object identifier, String message,
                                             Map<String, Object> details) {
        return new ConflictException(code, message, resource, identifier, details);
    }

    public static ConflictException reason(String message) {
        return new ConflictException("RESOURCE_CONFLICT", message, null, null);
    }
}
