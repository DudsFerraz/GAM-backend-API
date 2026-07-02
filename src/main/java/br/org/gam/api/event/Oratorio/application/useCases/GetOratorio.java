package br.org.gam.api.event.Oratorio.application.useCases;

import br.org.gam.api.event.application.EventNotFoundException;
import br.org.gam.api.event.application.EventSecurity;
import br.org.gam.api.event.application.EventEntityLoader;
import br.org.gam.api.event.Oratorio.application.OratorioMapper;
import br.org.gam.api.event.Oratorio.application.OratorioRDTO;
import br.org.gam.api.event.Oratorio.application.OratorioEntityLoader;
import br.org.gam.api.event.Oratorio.domain.Oratorio;
import br.org.gam.api.event.Oratorio.persistence.OratorioEntity;
import br.org.gam.api.event.persistence.EventEntity;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetOratorio {
    private final OratorioMapper oratorioMapper;
    private final EventEntityLoader getEventInstance;
    private final EventSecurity eventSecurity;
    private final OratorioEntityLoader getOratorioInstance;

    public GetOratorio(OratorioMapper oratorioMapper, EventEntityLoader getEventInstance, EventSecurity eventSecurity, OratorioEntityLoader getOratorioInstance) {
        this.oratorioMapper = oratorioMapper;
        this.getOratorioInstance = getOratorioInstance;
        this.getEventInstance = getEventInstance;
        this.eventSecurity = eventSecurity;
    }
    public OratorioRDTO byId(UUID id) {
        EventEntity eventEntity = getEventInstance.requiredById(id);
        if(!eventSecurity.canGetEvent(eventEntity)) throw new EventNotFoundException("Could not find oratorio with id " + id);

        OratorioEntity oratorioEntity = getOratorioInstance.requiredById(id);
        return oratorioMapper.entityToRDTO(oratorioEntity);
    }
}
