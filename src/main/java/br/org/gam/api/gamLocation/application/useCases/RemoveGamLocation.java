package br.org.gam.api.gamLocation.application.useCases;

import br.org.gam.api.gamLocation.application.GamLocationEntityLoader;
import br.org.gam.api.gamLocation.persistence.GamLocationRepository;
import br.org.gam.api.gamLocation.persistence.GamLocationEntity;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.exception.ConflictException;
import br.org.gam.api.shared.validation.RequiredReason;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RemoveGamLocation {
    private final GamLocationEntityLoader loader;
    private final GamLocationRepository repository;
    private final ActivityEvents activityEvents;

    public RemoveGamLocation(GamLocationEntityLoader loader, GamLocationRepository repository,
                             ActivityEvents activityEvents) {
        this.loader = loader;
        this.repository = repository;
        this.activityEvents = activityEvents;
    }

    @Transactional
    public void remove(UUID id, RemoveGamLocationDTO dto) {
        String reason = RequiredReason.normalize(dto.reason(), "GamLocation removal requires an audit reason.");
        GamLocationEntity entity = loader.requiredByIdForUpdate(id);
        long referenceCount = repository.countEventReferencesIncludingDeleted(id);
        if (referenceCount > 0) {
            throw ConflictException.resource(
                    "GAM_LOCATION_IN_USE",
                    "GamLocation",
                    id,
                    "GamLocation is referenced by " + referenceCount + " Event record(s).",
                    Map.of("eventReferenceCount", referenceCount)
            );
        }

        repository.delete(entity);
        activityEvents.gamLocationRemoved(id, reason, entity.getName());
    }
}
