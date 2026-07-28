package br.org.gam.api.shared.exception;

public class RequestValidationException extends RuntimeException {

    private final String location;
    private final String field;
    private final String violationCode;

    public RequestValidationException(String location, String field, String violationCode) {
        super("The request contains invalid input.");
        this.location = location;
        this.field = field;
        this.violationCode = violationCode;
    }

    public String getLocation() {
        return location;
    }

    public String getField() {
        return field;
    }

    public String getViolationCode() {
        return violationCode;
    }
}
