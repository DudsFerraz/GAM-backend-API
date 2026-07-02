package br.org.gam.api.event.Oratorio.application.useCases.CreateOratorio;

import br.org.gam.api.event.Oratorio.domain.Oratorio;

public interface CreateOratorio {
    CreateOratorioRDTO create(CreateOratorioDTO createOratorioDto);
}
