package br.org.gam.api.event.application.useCases;

import br.org.gam.api.event.application.EventMapper;
import br.org.gam.api.event.application.EventNotFoundException;
import br.org.gam.api.event.application.EventRDTO;
import br.org.gam.api.event.application.EventSecurity;
import br.org.gam.api.event.application.EventEntityLoader;
import br.org.gam.api.event.persistence.EventEntity;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetEvent {
    private final EventEntityLoader getEventInstance;
    private final EventMapper eventMapper;
    private final EventSecurity eventSecurity;

    public GetEvent(EventEntityLoader getEventInstance, EventMapper eventMapper, EventSecurity eventSecurity) {
        this.getEventInstance = getEventInstance;
        this.eventMapper = eventMapper;
        this.eventSecurity = eventSecurity;
    }
    public EventRDTO byId(UUID id) {
        EventEntity eventEntity = getEventInstance.requiredById(id);

        if(!eventSecurity.canGetEvent(eventEntity)) throw new EventNotFoundException("Could not find event with id " + id);

        return eventMapper.entityToEventRDTO(eventEntity);
    }

}
