package br.org.gam.api.event.Oratorio.application.useCases.GetOratorioInstance;

import br.org.gam.api.event.Oratorio.application.OratorioMapper;
import br.org.gam.api.event.Oratorio.application.OratorioNotFoundException;
import br.org.gam.api.event.Oratorio.domain.Oratorio;
import br.org.gam.api.event.Oratorio.persistence.OratorioEntity;
import br.org.gam.api.event.Oratorio.persistence.OratorioRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetOratorioInstance {
    private final OratorioRepository oratorioRepo;
    private final OratorioMapper oratorioMapper;

    public GetOratorioInstance(OratorioRepository oratorioRepo, OratorioMapper oratorioMapper) {
        this.oratorioRepo = oratorioRepo;
        this.oratorioMapper = oratorioMapper;
    }
    public Oratorio domainById(UUID id) {
        return oratorioRepo.findById(id)
                .map(oratorioMapper::entityToDomain)
                .orElseThrow(() -> new OratorioNotFoundException("Could not find oratorio with id " + id));
    }
    public OratorioEntity entityById(UUID id) {
        return oratorioRepo.findById(id)
                .orElseThrow(() -> new OratorioNotFoundException("Could not find oratorio with id " + id));
    }
}
