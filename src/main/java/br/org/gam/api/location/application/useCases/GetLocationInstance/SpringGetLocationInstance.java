package br.org.gam.api.location.application.useCases.GetLocationInstance;

import br.org.gam.api.location.application.LocationNotFoundException;
import br.org.gam.api.location.persistence.LocationEntity;
import br.org.gam.api.location.persistence.LocationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SpringGetLocationInstance implements GetLocationInstance {

    private final LocationRepository locationRepo;

    public SpringGetLocationInstance(LocationRepository locationRepo) {
        this.locationRepo = locationRepo;
    }

    @Override
    public LocationEntity entityById(UUID id) {
        return locationRepo.findById(id)
                .orElseThrow(() -> new LocationNotFoundException("Could not find location with id " + id));
    }
}
