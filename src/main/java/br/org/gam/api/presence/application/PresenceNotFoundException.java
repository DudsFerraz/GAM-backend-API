package br.org.gam.api.presence.application;

public class PresenceNotFoundException extends RuntimeException {
    public PresenceNotFoundException(String message) {
        super(message);
    }
}
