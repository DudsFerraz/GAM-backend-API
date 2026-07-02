package br.org.gam.api.event.Missa.application.useCases.CreateMissa;

import br.org.gam.api.event.Missa.domain.Missa;

public interface CreateMissa {
    CreateMissaRDTO createMissa(CreateMissaDTO dto);
}
