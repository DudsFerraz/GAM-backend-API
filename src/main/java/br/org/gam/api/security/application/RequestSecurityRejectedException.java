package br.org.gam.api.security.application;

import org.springframework.security.access.AccessDeniedException;

public final class RequestSecurityRejectedException extends AccessDeniedException {

    public RequestSecurityRejectedException() {
        super("Required request security proof was rejected.");
    }
}
