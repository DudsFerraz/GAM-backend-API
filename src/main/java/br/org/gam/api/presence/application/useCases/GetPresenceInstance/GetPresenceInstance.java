package br.org.gam.api.presence.application.useCases.GetPresenceInstance;

import br.org.gam.api.presence.domain.Presence;
import br.org.gam.api.presence.persistence.PresenceEntity;
import java.util.UUID;

public interface GetPresenceInstance {
    Presence domainById(UUID id);
    PresenceEntity entityById(UUID id);
    PresenceEntity entityByIds(UUID memberId, UUID eventId);
    Presence domainByIds(UUID memberId, UUID eventId);
}
