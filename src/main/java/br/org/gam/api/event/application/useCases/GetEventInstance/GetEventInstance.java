package br.org.gam.api.event.application.useCases.GetEventInstance;

import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.persistence.EventEntity;
import java.util.UUID;

public interface GetEventInstance {
    public Event domainById(UUID id);
    public EventEntity entityById(UUID id);
}
