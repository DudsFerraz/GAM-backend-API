package br.org.gam.api.event.oratorio.application;

import br.org.gam.api.event.oratorio.persistence.OratorioEntity;
import br.org.gam.api.event.oratorio.persistence.OratorioRepository;
import br.org.gam.api.shared.exception.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OratorioEntityLoader {

    private final OratorioRepository oratorioRepo;

    public OratorioEntityLoader(OratorioRepository oratorioRepo) {
        this.oratorioRepo = oratorioRepo;
    }

    public OratorioEntity requiredById(UUID id) {
        return oratorioRepo.findById(id)
                .orElseThrow(() -> NotFoundException.resource("Oratorio", id));
    }
}
