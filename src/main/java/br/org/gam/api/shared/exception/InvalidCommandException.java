package br.org.gam.api.shared.exception;

public class InvalidCommandException extends ApplicationException {
    private final String validationLocation;
    private final String validationField;
    private final String validationCode;

    private InvalidCommandException(String code, String message, String resource, Object identifier) {
        this(code, message, resource, identifier, null, null, null);
    }

    private InvalidCommandException(
            String code,
            String message,
            String resource,
            Object identifier,
            String validationLocation,
            String validationField,
            String validationCode
    ) {
        super(code, message, resource, identifier);
        this.validationLocation = validationLocation;
        this.validationField = validationField;
        this.validationCode = validationCode;
    }

    public static InvalidCommandException resource(String resource, Object identifier, String message) {
        return new InvalidCommandException("INVALID_COMMAND", message, resource, identifier);
    }

    public static InvalidCommandException reason(String message) {
        return new InvalidCommandException("INVALID_COMMAND", message, null, null);
    }

    public static InvalidCommandException reason(String code, String message) {
        return new InvalidCommandException(code, message, null, null);
    }

    public static InvalidCommandException validation(String location, String field, String violationCode) {
        return new InvalidCommandException(
                "INVALID_COMMAND",
                "The request contains invalid input.",
                null,
                null,
                location,
                field,
                violationCode
        );
    }

    public boolean hasValidationViolation() {
        return validationLocation != null && validationField != null && validationCode != null;
    }

    public String getValidationLocation() {
        return validationLocation;
    }

    public String getValidationField() {
        return validationField;
    }

    public String getValidationCode() {
        return validationCode;
    }
}
