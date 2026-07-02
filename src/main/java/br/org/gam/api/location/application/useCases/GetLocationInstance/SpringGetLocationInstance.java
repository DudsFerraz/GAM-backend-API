package br.org.gam.api.location.application.useCases.GetLocationInstance;

import br.org.gam.api.location.application.LocationMapper;
import br.org.gam.api.location.application.LocationNotFoundException;
import br.org.gam.api.location.domain.Location;
import br.org.gam.api.location.persistence.LocationEntity;
import br.org.gam.api.location.persistence.LocationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SpringGetLocationInstance implements GetLocationInstance {

    private final LocationRepository locationRepo;
    private final LocationMapper locationMapper;

    public SpringGetLocationInstance(LocationRepository locationRepo, LocationMapper locationMapper) {
        this.locationRepo = locationRepo;
        this.locationMapper = locationMapper;
    }

    @Override
    public Location domainById(UUID id) {
        return locationRepo.findById(id)
                .map(locationMapper::entityToDomain)
                .orElseThrow(() -> new LocationNotFoundException("Could not find location with id " + id));
    }

    @Override
    public LocationEntity entityById(UUID id) {
        return locationRepo.findById(id)
                .orElseThrow(() -> new LocationNotFoundException("Could not find location with id " + id));
    }
}
