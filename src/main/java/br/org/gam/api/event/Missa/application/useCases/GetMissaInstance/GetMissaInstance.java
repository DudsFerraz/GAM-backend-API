package br.org.gam.api.event.Missa.application.useCases.GetMissaInstance;

import br.org.gam.api.event.Missa.domain.Missa;
import br.org.gam.api.event.Missa.persistence.MissaEntity;
import java.util.UUID;

public interface GetMissaInstance {
    Missa domainById(UUID id);
    MissaEntity entityById(UUID id);
}
