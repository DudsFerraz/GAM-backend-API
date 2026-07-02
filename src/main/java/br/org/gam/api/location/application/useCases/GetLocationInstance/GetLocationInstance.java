package br.org.gam.api.location.application.useCases.GetLocationInstance;

import br.org.gam.api.location.persistence.LocationEntity;
import java.util.UUID;

public interface GetLocationInstance {
    LocationEntity entityById(UUID id);
}
