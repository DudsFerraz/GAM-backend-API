package br.org.gam.api.gamLocation.application.useCases;

import br.org.gam.api.gamLocation.application.GamLocationEntityLoader;
import br.org.gam.api.gamLocation.application.GamLocationMapper;
import br.org.gam.api.gamLocation.application.GamLocationRDTO;
import br.org.gam.api.gamLocation.persistence.GamLocationRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class GetGamLocations {
    private final GamLocationEntityLoader loader;
    private final GamLocationMapper mapper;
    private final GamLocationRepository repository;

    public GetGamLocations(GamLocationEntityLoader loader, GamLocationMapper mapper,
                           GamLocationRepository repository) {
        this.loader = loader;
        this.mapper = mapper;
        this.repository = repository;
    }

    public GamLocationRDTO byId(UUID id) {
        return mapper.entityToRDTO(loader.requiredById(id));
    }

    public Page<GamLocationRDTO> all(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::entityToRDTO);
    }
}
