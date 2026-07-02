package br.org.gam.api.event.application.useCases.GetEvent;

import br.org.gam.api.event.application.EventMapper;
import br.org.gam.api.event.application.EventNotFoundException;
import br.org.gam.api.event.application.EventRDTO;
import br.org.gam.api.event.application.EventSecurity;
import br.org.gam.api.event.application.useCases.GetEventInstance.GetEventInstance;
import br.org.gam.api.event.persistence.EventEntity;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetEvent {
    private final GetEventInstance getEventInstance;
    private final EventMapper eventMapper;
    private final EventSecurity eventSecurity;

    public GetEvent(GetEventInstance getEventInstance, EventMapper eventMapper, EventSecurity eventSecurity) {
        this.getEventInstance = getEventInstance;
        this.eventMapper = eventMapper;
        this.eventSecurity = eventSecurity;
    }
    public EventRDTO byId(UUID id) {
        EventEntity eventEntity = getEventInstance.entityById(id);

        if(!eventSecurity.canGetEvent(eventEntity)) throw new EventNotFoundException("Could not find event with id " + id);

        return eventMapper.entityToEventRDTO(eventEntity);
    }

}
