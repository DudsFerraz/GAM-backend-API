package br.org.gam.api.presence.application.useCases.GetPresenceInstance;

import br.org.gam.api.presence.application.PresenceNotFoundException;
import br.org.gam.api.presence.persistence.PresenceEntity;
import br.org.gam.api.presence.persistence.PresenceRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetPresenceInstance {
    private final PresenceRepository presenceRepo;

    public GetPresenceInstance(PresenceRepository presenceRepo) {
        this.presenceRepo = presenceRepo;
    }
    public PresenceEntity entityById(UUID id) {
        return presenceRepo.findById(id)
                .orElseThrow(() -> new PresenceNotFoundException("Could not find presence with id " + id));
    }
    public PresenceEntity entityByIds(UUID memberId, UUID eventId) {
        return presenceRepo.findByMember_IdAndEvent_Id(memberId, eventId)
                .orElseThrow(() -> new PresenceNotFoundException(
                        String.format("member with id: %s has no presence registered in event with id: %s", memberId, eventId)
                ));
    }

}
