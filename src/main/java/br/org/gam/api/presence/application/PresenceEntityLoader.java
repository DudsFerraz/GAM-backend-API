package br.org.gam.api.presence.application;

import br.org.gam.api.presence.persistence.PresenceEntity;
import br.org.gam.api.presence.persistence.PresenceRepository;
import br.org.gam.api.shared.exception.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PresenceEntityLoader {

    private final PresenceRepository presenceRepo;

    public PresenceEntityLoader(PresenceRepository presenceRepo) {
        this.presenceRepo = presenceRepo;
    }

    public PresenceEntity requiredById(UUID id) {
        return presenceRepo.findById(id)
                .orElseThrow(() -> NotFoundException.resource("Presence", id));
    }

    public PresenceEntity requiredByMemberIdAndEventId(UUID memberId, UUID eventId) {
        return presenceRepo.findByMember_IdAndEvent_Id(memberId, eventId)
                .orElseThrow(() -> NotFoundException.resource("Presence", "%s:%s".formatted(memberId, eventId)));
    }
}
