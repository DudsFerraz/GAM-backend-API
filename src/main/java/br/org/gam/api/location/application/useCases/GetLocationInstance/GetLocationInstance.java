package br.org.gam.api.location.application.useCases.GetLocationInstance;

import br.org.gam.api.location.domain.Location;
import br.org.gam.api.location.persistence.LocationEntity;
import java.util.UUID;

public interface GetLocationInstance {
    public Location domainById(UUID id);
    public LocationEntity entityById(UUID id);
}
