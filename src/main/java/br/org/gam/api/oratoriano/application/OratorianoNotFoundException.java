package br.org.gam.api.oratoriano.application;

public class OratorianoNotFoundException extends RuntimeException {
    public OratorianoNotFoundException(String message) {
        super(message);
    }
}
