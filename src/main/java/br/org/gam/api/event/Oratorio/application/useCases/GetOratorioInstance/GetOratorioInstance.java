package br.org.gam.api.event.Oratorio.application.useCases.GetOratorioInstance;

import br.org.gam.api.event.Oratorio.domain.Oratorio;
import br.org.gam.api.event.Oratorio.persistence.OratorioEntity;
import java.util.UUID;

public interface GetOratorioInstance {
    Oratorio domainById(UUID id);
    OratorioEntity entityById(UUID id);
}
