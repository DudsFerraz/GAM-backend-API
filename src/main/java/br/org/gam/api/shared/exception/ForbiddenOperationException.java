package br.org.gam.api.shared.exception;

public class ForbiddenOperationException extends ApplicationException {

    private ForbiddenOperationException(String code, String message, String resource, Object identifier) {
        super(code, message, resource, identifier);
    }

    public static ForbiddenOperationException resource(String resource, Object identifier, String message) {
        return new ForbiddenOperationException("FORBIDDEN_OPERATION", message, resource, identifier);
    }

    public static ForbiddenOperationException reason(String message) {
        return new ForbiddenOperationException("FORBIDDEN_OPERATION", message, null, null);
    }
}
