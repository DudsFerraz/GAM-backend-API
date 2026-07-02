package br.org.gam.api.event.application.useCases.GetEventInstance;

import br.org.gam.api.event.application.EventMapper;
import br.org.gam.api.event.application.EventNotFoundException;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.event.persistence.EventRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SpringGetEventInstance implements GetEventInstance {

    private final EventRepository eventRepo;
    private final EventMapper eventMapper;

    public SpringGetEventInstance(EventRepository eventRepo, EventMapper eventMapper) {
        this.eventRepo = eventRepo;
        this.eventMapper = eventMapper;
    }

    @Override
    public Event domainById(UUID id) {
        return eventRepo.findById(id)
                .map(eventMapper::entityToDomain)
                .orElseThrow(() -> new EventNotFoundException("Could not find event with id " + id));
    }

    @Override
    public EventEntity entityById(UUID id) {
        return eventRepo.findById(id)
                .orElseThrow(() -> new EventNotFoundException("Could not find event with id " + id));
    }
}
