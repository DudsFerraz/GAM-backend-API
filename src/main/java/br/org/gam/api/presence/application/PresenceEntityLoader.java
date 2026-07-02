package br.org.gam.api.presence.application;

import br.org.gam.api.presence.persistence.PresenceEntity;
import br.org.gam.api.presence.persistence.PresenceRepository;
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
                .orElseThrow(() -> new PresenceNotFoundException("Could not find presence with id " + id));
    }

    public PresenceEntity requiredByMemberIdAndEventId(UUID memberId, UUID eventId) {
        return presenceRepo.findByMember_IdAndEvent_Id(memberId, eventId)
                .orElseThrow(() -> new PresenceNotFoundException(
                        String.format("member with id: %s has no presence registered in event with id: %s", memberId, eventId)
                ));
    }
}
