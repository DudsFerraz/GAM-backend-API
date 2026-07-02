package br.org.gam.api.event.Oratorio.application;

import br.org.gam.api.event.Oratorio.persistence.OratorioEntity;
import br.org.gam.api.event.Oratorio.persistence.OratorioRepository;
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
                .orElseThrow(() -> new OratorioNotFoundException("Could not find oratorio with id " + id));
    }
}
