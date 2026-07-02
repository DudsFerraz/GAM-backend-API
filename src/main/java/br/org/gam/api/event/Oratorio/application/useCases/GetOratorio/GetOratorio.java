package br.org.gam.api.event.Oratorio.application.useCases.GetOratorio;

import br.org.gam.api.event.Oratorio.application.OratorioRDTO;
import br.org.gam.api.event.Oratorio.domain.Oratorio;
import java.util.UUID;

public interface GetOratorio {
    OratorioRDTO byId(UUID id);
}
