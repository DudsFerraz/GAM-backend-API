package br.org.gam.api.Entities.events.oratorio.services.getOratorio;

import br.org.gam.api.Entities.events.generic.exception.EventNotFoundException;
import br.org.gam.api.Entities.events.generic.persistence.EventEntity;
import br.org.gam.api.Entities.events.generic.security.EventSecurity;
import br.org.gam.api.Entities.events.generic.services.getEventInstance.GetEventInstance;
import br.org.gam.api.Entities.events.oratorio.OratorioMapper;
import br.org.gam.api.Entities.events.oratorio.persistence.OratorioEntity;
import br.org.gam.api.Entities.events.oratorio.services.OratorioRDTO;
import br.org.gam.api.Entities.events.oratorio.services.getOratorioInstance.GetOratorioInstance;
import org.springframework.stereotype.Service;

import java.util.UUID;

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
