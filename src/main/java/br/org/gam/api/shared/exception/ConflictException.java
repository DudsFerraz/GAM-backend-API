package br.org.gam.api.shared.exception;

public class ConflictException extends ApplicationException {

    private ConflictException(String code, String message, String resource, Object identifier) {
        super(code, message, resource, identifier);
    }

    public static ConflictException resource(String resource, Object identifier, String message) {
        return new ConflictException("RESOURCE_CONFLICT", message, resource, identifier);
    }

    public static ConflictException reason(String message) {
        return new ConflictException("RESOURCE_CONFLICT", message, null, null);
    }
}
