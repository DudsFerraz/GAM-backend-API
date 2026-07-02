package br.org.gam.api.event.application;

import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.event.persistence.EventRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EventEntityLoader {

    private final EventRepository eventRepo;

    public EventEntityLoader(EventRepository eventRepo) {
        this.eventRepo = eventRepo;
    }

    public EventEntity requiredById(UUID id) {
        return eventRepo.findById(id)
                .orElseThrow(() -> new EventNotFoundException("Could not find event with id " + id));
    }
}
