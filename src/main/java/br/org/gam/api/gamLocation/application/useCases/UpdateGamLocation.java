package br.org.gam.api.gamLocation.application.useCases;

import br.org.gam.api.gamLocation.application.GamLocationEntityLoader;
import br.org.gam.api.gamLocation.application.GamLocationDuplicateLookup;
import br.org.gam.api.gamLocation.application.GamLocationMapper;
import br.org.gam.api.gamLocation.application.GamLocationNormalizer;
import br.org.gam.api.gamLocation.application.GamLocationRDTO;
import br.org.gam.api.gamLocation.persistence.GamLocationRepository;
import br.org.gam.api.gamLocation.persistence.GamLocationEntity;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.exception.ConflictException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateGamLocation {
    private final GamLocationRepository repository;
    private final GamLocationEntityLoader loader;
    private final GamLocationMapper mapper;
    private final GamLocationDuplicateLookup duplicateLookup;
    private final ActivityEvents activityEvents;

    public UpdateGamLocation(GamLocationRepository repository, GamLocationEntityLoader loader,
                             GamLocationMapper mapper, ActivityEvents activityEvents,
                             GamLocationDuplicateLookup duplicateLookup) {
        this.repository = repository;
        this.loader = loader;
        this.mapper = mapper;
        this.activityEvents = activityEvents;
        this.duplicateLookup = duplicateLookup;
    }

    @Transactional
    public GamLocationRDTO update(UUID id, GamLocationMutationDTO dto) {
        GamLocationNormalizer.Values values = GamLocationNormalizer.normalize(
                dto.name(), dto.street(), dto.city(), dto.state(), dto.postalCode(), dto.countryCode(),
                dto.latitude(), dto.longitude()
        );
        GamLocationEntity entity = loader.requiredByIdForUpdate(id);

        List<String> changedFields = changedFields(entity, values);
        if (changedFields.isEmpty()) {
            return mapper.entityToRDTO(entity);
        }

        Optional<GamLocationEntity> duplicate = repository.findActiveDuplicateExcluding(
                id, values.identityName(), values.identityStreet(), values.identityCity(), values.identityState(),
                values.identityPostalCode(), values.identityCountryCode()
        );
        if (duplicate.isPresent()) {
            throw CreateGamLocation.duplicateConflict(duplicate.get());
        }

        CreateGamLocation.apply(entity, values);
        try {
            repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            Optional<GamLocationEntity> concurrentDuplicate = isDuplicateConstraint(exception)
                    ? duplicateLookup.findActiveDuplicateExcluding(id, values)
                    : Optional.empty();
            if (concurrentDuplicate.isPresent()) {
                throw CreateGamLocation.duplicateConflict(concurrentDuplicate.get());
            }
            throw exception;
        }

        activityEvents.gamLocationUpdated(id, changedFields);
        return mapper.entityToRDTO(entity);
    }

    private List<String> changedFields(GamLocationEntity entity, GamLocationNormalizer.Values values) {
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(entity.getName(), values.name())) changed.add("name");
        if (!Objects.equals(entity.getStreet(), values.street())) changed.add("street");
        if (!Objects.equals(entity.getCity(), values.city())) changed.add("city");
        if (!Objects.equals(entity.getState(), values.state())) changed.add("state");
        if (!Objects.equals(entity.getPostalCode(), values.postalCode())) changed.add("postalCode");
        if (!Objects.equals(entity.getCountryCode(), values.countryCode())) changed.add("countryCode");
        if (!sameCoordinate(entity.getLatitude(), values.latitude())) changed.add("latitude");
        if (!sameCoordinate(entity.getLongitude(), values.longitude())) changed.add("longitude");
        return changed;
    }

    private boolean sameCoordinate(BigDecimal current, BigDecimal replacement) {
        return current == null ? replacement == null : replacement != null && current.compareTo(replacement) == 0;
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
