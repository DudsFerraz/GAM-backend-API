package br.org.gam.api.presence.application.useCases.GetPresence;

import br.org.gam.api.presence.application.PresenceRDTO;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GetPresence {
    PresenceRDTO byIds(UUID memberId, UUID eventId);
    Page<PresenceRDTO> allByEvent(UUID eventId, Pageable pageable);
    Page<PresenceRDTO> allByMember(UUID memberId, Pageable pageable);
}
