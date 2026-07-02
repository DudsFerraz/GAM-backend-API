package br.org.gam.api.location.application;

import br.org.gam.api.location.persistence.LocationEntity;
import br.org.gam.api.location.persistence.LocationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LocationEntityLoader {

    private final LocationRepository locationRepo;

    public LocationEntityLoader(LocationRepository locationRepo) {
        this.locationRepo = locationRepo;
    }

    public LocationEntity requiredById(UUID id) {
        return locationRepo.findById(id)
                .orElseThrow(() -> new LocationNotFoundException("Could not find location with id " + id));
    }
}
