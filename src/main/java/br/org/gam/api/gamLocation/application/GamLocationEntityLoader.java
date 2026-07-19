package br.org.gam.api.gamLocation.application;

import br.org.gam.api.gamLocation.persistence.GamLocationRepository;
import br.org.gam.api.gamLocation.persistence.GamLocationEntity;
import br.org.gam.api.shared.exception.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GamLocationEntityLoader {
    private final GamLocationRepository repository;

    public GamLocationEntityLoader(GamLocationRepository repository) {
        this.repository = repository;
    }

    public GamLocationEntity requiredById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> NotFoundException.resource("GamLocation", id));
    }

    public GamLocationEntity requiredByIdForUpdate(UUID id) {
        return repository.findActiveByIdForUpdate(id)
                .orElseThrow(() -> NotFoundException.resource("GamLocation", id));
    }
}
