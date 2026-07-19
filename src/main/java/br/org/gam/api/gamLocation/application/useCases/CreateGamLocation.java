package br.org.gam.api.gamLocation.application.useCases;

import br.org.gam.api.gamLocation.application.GamLocationMapper;
import br.org.gam.api.gamLocation.application.GamLocationDuplicateLookup;
import br.org.gam.api.gamLocation.application.GamLocationNormalizer;
import br.org.gam.api.gamLocation.application.GamLocationRDTO;
import br.org.gam.api.gamLocation.persistence.GamLocationRepository;
import br.org.gam.api.gamLocation.persistence.GamLocationEntity;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.exception.ConflictException;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateGamLocation {
    private final GamLocationRepository repository;
    private final GamLocationMapper mapper;
    private final GamLocationDuplicateLookup duplicateLookup;
    private final ActivityEvents activityEvents;

    public CreateGamLocation(GamLocationRepository repository, GamLocationMapper mapper,
                             ActivityEvents activityEvents, GamLocationDuplicateLookup duplicateLookup) {
        this.repository = repository;
        this.mapper = mapper;
        this.activityEvents = activityEvents;
        this.duplicateLookup = duplicateLookup;
    }

    @Transactional
    public GamLocationRDTO create(GamLocationMutationDTO dto) {
        GamLocationNormalizer.Values values = values(dto);
        Optional<GamLocationEntity> duplicate = repository.findActiveDuplicate(
                values.identityName(), values.identityStreet(), values.identityCity(), values.identityState(),
                values.identityPostalCode(), values.identityCountryCode()
        );
        if (duplicate.isPresent()) {
            throw duplicateConflict(duplicate.get());
        }

        GamLocationEntity entity = new GamLocationEntity();
        entity.setId(UUIDGenerator.generateUUIDV7());
        apply(entity, values);

        GamLocationEntity saved;
        try {
            saved = repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            Optional<GamLocationEntity> concurrentDuplicate = isDuplicateConstraint(exception)
                    ? duplicateLookup.findActiveDuplicate(values)
                    : Optional.empty();
            if (concurrentDuplicate.isPresent()) {
                throw duplicateConflict(concurrentDuplicate.get());
            }
            throw exception;
        }

        activityEvents.gamLocationCreated(saved.getId());
        return mapper.entityToRDTO(saved);
    }

    private GamLocationNormalizer.Values values(GamLocationMutationDTO dto) {
        return GamLocationNormalizer.normalize(
                dto.name(), dto.street(), dto.city(), dto.state(), dto.postalCode(), dto.countryCode(),
                dto.latitude(), dto.longitude()
        );
    }

    static void apply(GamLocationEntity entity, GamLocationNormalizer.Values values) {
        entity.setName(values.name());
        entity.setStreet(values.street());
        entity.setCity(values.city());
        entity.setState(values.state());
        entity.setPostalCode(values.postalCode());
        entity.setCountryCode(values.countryCode());
        entity.setLatitude(values.latitude());
        entity.setLongitude(values.longitude());
        entity.setIdentityName(values.identityName());
        entity.setIdentityStreet(values.identityStreet());
        entity.setIdentityCity(values.identityCity());
        entity.setIdentityState(values.identityState());
        entity.setIdentityPostalCode(values.identityPostalCode());
        entity.setIdentityCountryCode(values.identityCountryCode());
    }

    static ConflictException duplicateConflict(GamLocationEntity existing) {
        return ConflictException.resource(
                "GAM_LOCATION_ALREADY_EXISTS",
                "GamLocation",
                existing.getId(),
                "An active GamLocation with the same normalized duplicate identity already exists."
        );
    }

    private boolean isDuplicateConstraint(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException violation
                    && "idx_gam_location_active_duplicate_identity".equals(violation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return exception.getMessage() != null
                && exception.getMessage().contains("idx_gam_location_active_duplicate_identity");
    }
}
