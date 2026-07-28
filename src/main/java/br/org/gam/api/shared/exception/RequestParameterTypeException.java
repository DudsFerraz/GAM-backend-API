package br.org.gam.api.shared.exception;

public class RequestParameterTypeException extends RuntimeException {

    private final String location;
    private final String field;
    private final String expectedType;

    public RequestParameterTypeException(String location, String field, String expectedType) {
        super("A request parameter has an incompatible type.");
        this.location = location;
        this.field = field;
        this.expectedType = expectedType;
    }

    public String getLocation() {
        return location;
    }

    public String getField() {
        return field;
    }

    public String getExpectedType() {
        return expectedType;
    }
}
