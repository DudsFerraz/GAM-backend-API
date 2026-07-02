package br.org.gam.api.presence.application;

public class PresenceConflictException extends RuntimeException {
    public PresenceConflictException(String message) {
        super(message);
    }
}
