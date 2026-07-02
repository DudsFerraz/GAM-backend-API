package br.org.gam.api.presence.application.useCases.GetPresenceInstance;

import br.org.gam.api.presence.persistence.PresenceEntity;
import java.util.UUID;

public interface GetPresenceInstance {
    PresenceEntity entityById(UUID id);
    PresenceEntity entityByIds(UUID memberId, UUID eventId);
}
