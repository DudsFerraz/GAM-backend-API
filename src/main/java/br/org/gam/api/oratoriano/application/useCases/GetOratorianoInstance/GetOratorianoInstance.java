package br.org.gam.api.oratoriano.application.useCases.GetOratorianoInstance;

import br.org.gam.api.oratoriano.domain.Oratoriano;
import br.org.gam.api.oratoriano.persistence.OratorianoEntity;
import java.util.Set;
import java.util.UUID;

public interface GetOratorianoInstance {
    Oratoriano domainById(UUID id);
    OratorianoEntity entityById(UUID id);
    Set<Oratoriano> domainsbyId(Set<UUID> ids);
}
