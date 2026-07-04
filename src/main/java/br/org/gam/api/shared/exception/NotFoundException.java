package br.org.gam.api.shared.exception;

public class NotFoundException extends ApplicationException {

    private NotFoundException(String resource, Object identifier) {
        super(
                "RESOURCE_NOT_FOUND",
                "%s not found with identifier %s".formatted(resource, identifier),
                resource,
                identifier
        );
    }

    public static NotFoundException resource(String resource, Object identifier) {
        return new NotFoundException(resource, identifier);
    }
}
