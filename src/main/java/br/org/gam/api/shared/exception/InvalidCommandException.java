package br.org.gam.api.shared.exception;

public class InvalidCommandException extends ApplicationException {

    private InvalidCommandException(String code, String message, String resource, Object identifier) {
        super(code, message, resource, identifier);
    }

    public static InvalidCommandException resource(String resource, Object identifier, String message) {
        return new InvalidCommandException("INVALID_COMMAND", message, resource, identifier);
    }

    public static InvalidCommandException reason(String message) {
        return new InvalidCommandException("INVALID_COMMAND", message, null, null);
    }
}
