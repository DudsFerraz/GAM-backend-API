package br.org.gam.api.oratoriano.application;

import br.org.gam.api.oratoriano.domain.Oratoriano;
import br.org.gam.api.oratoriano.persistence.OratorianoRepository;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class OratorianoDomainLoader {

    private final OratorianoRepository oratorianoRepo;
    private final OratorianoMapper oratorianoMapper;

    public OratorianoDomainLoader(OratorianoRepository oratorianoRepo, OratorianoMapper oratorianoMapper) {
        this.oratorianoRepo = oratorianoRepo;
        this.oratorianoMapper = oratorianoMapper;
    }

    public Oratoriano requiredById(UUID id) {
        return oratorianoRepo.findById(id)
                .map(oratorianoMapper::entityToDomain)
                .orElseThrow(() -> new OratorianoNotFoundException("Could not find oratoriano with id " + id));
    }

    public Set<Oratoriano> requiredByIds(Set<UUID> ids) {
        return oratorianoRepo.findAllById(ids)
                .stream()
                .map(oratorianoMapper::entityToDomain)
                .collect(Collectors.toSet());
    }
}
