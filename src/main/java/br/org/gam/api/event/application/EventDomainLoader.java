package br.org.gam.api.event.application;

import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.persistence.EventRepository;
import br.org.gam.api.shared.exception.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EventDomainLoader {

    private final EventRepository eventRepo;
    private final EventMapper eventMapper;

    public EventDomainLoader(EventRepository eventRepo, EventMapper eventMapper) {
        this.eventRepo = eventRepo;
        this.eventMapper = eventMapper;
    }

    public Event requiredById(UUID id) {
        return eventRepo.findById(id)
                .map(eventMapper::entityToDomain)
                .orElseThrow(() -> NotFoundException.resource("Event", id));
    }
}
