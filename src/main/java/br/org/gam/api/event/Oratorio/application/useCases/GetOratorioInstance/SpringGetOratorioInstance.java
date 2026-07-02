package br.org.gam.api.event.Oratorio.application.useCases.GetOratorioInstance;

import br.org.gam.api.event.Oratorio.application.OratorioMapper;
import br.org.gam.api.event.Oratorio.application.OratorioNotFoundException;
import br.org.gam.api.event.Oratorio.domain.Oratorio;
import br.org.gam.api.event.Oratorio.persistence.OratorioEntity;
import br.org.gam.api.event.Oratorio.persistence.OratorioRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SpringGetOratorioInstance implements GetOratorioInstance {
    private final OratorioRepository oratorioRepo;
    private final OratorioMapper oratorioMapper;

    public SpringGetOratorioInstance(OratorioRepository oratorioRepo, OratorioMapper oratorioMapper) {
        this.oratorioRepo = oratorioRepo;
        this.oratorioMapper = oratorioMapper;
    }

    @Override
    public Oratorio domainById(UUID id) {
        return oratorioRepo.findById(id)
                .map(oratorioMapper::entityToDomain)
                .orElseThrow(() -> new OratorioNotFoundException("Could not find oratorio with id " + id));
    }

    @Override
    public OratorioEntity entityById(UUID id) {
        return oratorioRepo.findById(id)
                .orElseThrow(() -> new OratorioNotFoundException("Could not find oratorio with id " + id));
    }
}
