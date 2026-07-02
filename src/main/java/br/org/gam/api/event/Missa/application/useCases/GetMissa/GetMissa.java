package br.org.gam.api.event.Missa.application.useCases.GetMissa;

import br.org.gam.api.event.Missa.application.MissaRDTO;
import br.org.gam.api.event.Missa.domain.Missa;
import br.org.gam.api.event.Missa.persistence.MissaEntity;
import java.util.UUID;

public interface GetMissa {
    MissaRDTO byId(UUID id);
}
