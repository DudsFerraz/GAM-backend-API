package br.org.gam.api.oratoriano.application.useCases.GetOratorianoInstance;

import br.org.gam.api.oratoriano.application.OratorianoMapper;
import br.org.gam.api.oratoriano.application.OratorianoNotFoundException;
import br.org.gam.api.oratoriano.domain.Oratoriano;
import br.org.gam.api.oratoriano.persistence.OratorianoEntity;
import br.org.gam.api.oratoriano.persistence.OratorianoRepository;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SpringGetOratorianoInstance implements GetOratorianoInstance {
    private final OratorianoRepository oratorianoRepo;
    private final OratorianoMapper oratorianoMapper;

    public SpringGetOratorianoInstance(OratorianoRepository oratorianoRepo, OratorianoMapper oratorianoMapper) {
        this.oratorianoRepo = oratorianoRepo;
        this.oratorianoMapper = oratorianoMapper;
    }

    @Override
    public Oratoriano domainById(UUID id) {
        return oratorianoRepo.findById(id)
                .map(oratorianoMapper::entityToDomain)
                .orElseThrow(() -> new OratorianoNotFoundException("Could not find oratoriano with id " + id));
    }

    @Override
    public OratorianoEntity entityById(UUID id) {
        return oratorianoRepo.findById(id)
                .orElseThrow(() -> new OratorianoNotFoundException("Could not find oratoriano with id " + id));
    }

    @Override
    public Set<Oratoriano> domainsbyId(Set<UUID> ids) {
        return oratorianoRepo.findAllById(ids)
                .stream()
                .map(oratorianoMapper::entityToDomain)
                .collect(Collectors.toSet());
    }
}
