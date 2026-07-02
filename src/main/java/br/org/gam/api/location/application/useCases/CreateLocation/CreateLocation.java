package br.org.gam.api.location.application.useCases.CreateLocation;

import br.org.gam.api.location.application.LocationMapper;
import br.org.gam.api.location.persistence.LocationEntity;
import br.org.gam.api.location.persistence.LocationRepository;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import jakarta.transaction.Transactional;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class CreateLocation {

    private final LocationRepository locationRepo;
    private final LocationMapper locationMapper;

    public CreateLocation(LocationRepository locationRepo, LocationMapper locationMapper) {
        this.locationRepo = locationRepo;
        this.locationMapper = locationMapper;
    }

    @Transactional
    public CreateLocationRDTO create(CreateLocationDTO dto) {

        Objects.requireNonNull(dto.name(), "Name cannot be null");
        Objects.requireNonNull(dto.city(), "City cannot be null");
        Objects.requireNonNull(dto.state(), "State cannot be null");
        Objects.requireNonNull(dto.countryCode(), "CountryCode cannot be null");

        LocationEntity locationEntity = new LocationEntity();
        locationEntity.setId(UUIDGenerator.generateUUIDV7());
        locationEntity.setName(dto.name().trim());
        locationEntity.setStreet(dto.street() == null ? "" : dto.street().trim());
        locationEntity.setCity(dto.city().trim());
        locationEntity.setState(dto.state().trim());
        locationEntity.setPostalCode(dto.postalCode() == null ? "" : dto.postalCode().trim());
        locationEntity.setCountryCode(dto.countryCode().trim());
        locationEntity.setLatitude(dto.latitude());
        locationEntity.setLongitude(dto.longitude());

        LocationEntity savedLocationEntity = locationRepo.save(locationEntity);

        return locationMapper.entityToCreateLocationRDTO(savedLocationEntity);
    }
}
