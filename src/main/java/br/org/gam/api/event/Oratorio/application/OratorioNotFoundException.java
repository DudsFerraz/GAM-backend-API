package br.org.gam.api.event.Oratorio.application;

import br.org.gam.api.event.Oratorio.domain.Oratorio;

public class OratorioNotFoundException extends RuntimeException {
    public OratorioNotFoundException(String message) {
        super(message);
    }
}
