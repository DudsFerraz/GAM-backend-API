package br.org.gam.api.shared.exception;

import lombok.Getter;

@Getter
public abstract class ApplicationException extends RuntimeException {

    private final String code;
    private final String resource;
    private final Object identifier;

    protected ApplicationException(String code, String message, String resource, Object identifier) {
        super(message);
        this.code = code;
        this.resource = resource;
        this.identifier = identifier;
    }

}
