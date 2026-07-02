package br.org.gam.api.event.application.useCases.GetEvent;

import br.org.gam.api.event.application.EventRDTO;
import java.util.UUID;

public interface GetEvent {
    EventRDTO byId(UUID id);
}
