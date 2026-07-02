package br.org.gam.api.event.Missa.application;

import br.org.gam.api.event.Missa.domain.Missa;

public class MissaNotFoundException extends RuntimeException {
    public MissaNotFoundException(String message) {
        super(message);
    }
}
