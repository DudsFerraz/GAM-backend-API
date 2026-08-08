package br.org.gam.api.event.oratorio.application.useCases;

import br.org.gam.api.event.application.EventSecurity;
import br.org.gam.api.event.application.EventEntityLoader;
import br.org.gam.api.event.oratorio.application.OratorioMapper;
import br.org.gam.api.event.oratorio.application.OratorioRDTO;
import br.org.gam.api.event.oratorio.application.OratorioEntityLoader;
import br.org.gam.api.event.oratorio.persistence.OratorioEntity;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.shared.exception.NotFoundException;
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
        if(!eventSecurity.canGetEvent(eventEntity)) throw NotFoundException.resource("Oratorio", id);

        OratorioEntity oratorioEntity = getOratorioInstance.requiredById(id);
        return oratorioMapper.entityToRDTO(oratorioEntity);
    }
}
