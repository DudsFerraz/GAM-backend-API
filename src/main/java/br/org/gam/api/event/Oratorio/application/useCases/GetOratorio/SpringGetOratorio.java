package br.org.gam.api.event.Oratorio.application.useCases.GetOratorio;

import br.org.gam.api.event.application.EventNotFoundException;
import br.org.gam.api.event.application.EventSecurity;
import br.org.gam.api.event.application.useCases.GetEventInstance.GetEventInstance;
import br.org.gam.api.event.Oratorio.application.OratorioMapper;
import br.org.gam.api.event.Oratorio.application.OratorioRDTO;
import br.org.gam.api.event.Oratorio.application.useCases.GetOratorioInstance.GetOratorioInstance;
import br.org.gam.api.event.Oratorio.domain.Oratorio;
import br.org.gam.api.event.Oratorio.persistence.OratorioEntity;
import br.org.gam.api.event.persistence.EventEntity;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SpringGetOratorio implements GetOratorio {
    private final OratorioMapper oratorioMapper;
    private final GetEventInstance getEventInstance;
    private final EventSecurity eventSecurity;
    private final GetOratorioInstance getOratorioInstance;

    public SpringGetOratorio(OratorioMapper oratorioMapper, GetEventInstance getEventInstance, EventSecurity eventSecurity, GetOratorioInstance getOratorioInstance) {
        this.oratorioMapper = oratorioMapper;
        this.getOratorioInstance = getOratorioInstance;
        this.getEventInstance = getEventInstance;
        this.eventSecurity = eventSecurity;
    }

    @Override
    public OratorioRDTO byId(UUID id) {
        EventEntity eventEntity = getEventInstance.entityById(id);
        if(!eventSecurity.canGetEvent(eventEntity)) throw new EventNotFoundException("Could not find oratorio with id " + id);

        OratorioEntity oratorioEntity = getOratorioInstance.entityById(id);
        return oratorioMapper.entityToRDTO(oratorioEntity);
    }
}
