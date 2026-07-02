package br.org.gam.api.location.application.useCases.CreateLocation;

import br.org.gam.api.location.application.LocationMapper;
import br.org.gam.api.location.domain.Location;
import br.org.gam.api.location.persistence.LocationEntity;
import br.org.gam.api.location.persistence.LocationRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class SpringCreateLocation implements CreateLocation {

    private final LocationRepository locationRepo;
    private final LocationMapper locationMapper;

    public SpringCreateLocation(LocationRepository locationRepo, LocationMapper locationMapper) {
        this.locationRepo = locationRepo;
        this.locationMapper = locationMapper;
    }

    @Transactional
    @Override
    public CreateLocationRDTO create(CreateLocationDTO dto) {

        Location newLocation = Location.register(dto.name(), dto.street(), dto.city(), dto.state(), dto.postalCode(),
                                                dto.countryCode(), dto.latitude(), dto.longitude());
        LocationEntity locationEntity = locationMapper.domainToEntity(newLocation);
        LocationEntity savedLocationEntity = locationRepo.save(locationEntity);

        return locationMapper.entityToCreateLocationRDTO(savedLocationEntity);
    }
}
