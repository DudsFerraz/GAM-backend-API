package br.org.gam.api.presence.application.useCases.GetPresenceInstance;

import br.org.gam.api.presence.application.PresenceMapper;
import br.org.gam.api.presence.application.PresenceNotFoundException;
import br.org.gam.api.presence.domain.Presence;
import br.org.gam.api.presence.persistence.PresenceEntity;
import br.org.gam.api.presence.persistence.PresenceRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SpringGetPresenceInstance implements GetPresenceInstance {
    private final PresenceRepository presenceRepo;
    private final PresenceMapper presenceMapper;

    public SpringGetPresenceInstance(PresenceRepository presenceRepo, PresenceMapper presenceMapper) {
        this.presenceRepo = presenceRepo;
        this.presenceMapper = presenceMapper;
    }

    @Override
    public Presence domainById(UUID id) {
        return presenceRepo.findById(id)
                .map(presenceMapper::entityToDomain)
                .orElseThrow(() -> new PresenceNotFoundException("Could not find presence with id " + id));
    }

    @Override
    public PresenceEntity entityById(UUID id) {
        return presenceRepo.findById(id)
                .orElseThrow(() -> new PresenceNotFoundException("Could not find presence with id " + id));
    }

    @Override
    public PresenceEntity entityByIds(UUID memberId, UUID eventId) {
        return presenceRepo.findByMember_IdAndEvent_Id(memberId, eventId)
                .orElseThrow(() -> new PresenceNotFoundException(
                        String.format("member with id: %s has no presence registered in event with id: %s", memberId, eventId)
                ));
    }

    @Override
    public Presence domainByIds(UUID memberId, UUID eventId) {
        return presenceRepo.findByMember_IdAndEvent_Id(memberId, eventId)
                .map(presenceMapper::entityToDomain)
                .orElseThrow(() -> new PresenceNotFoundException(
                        String.format("member with id: %s has no presence registered in event with id: %s", memberId, eventId)
                ));
    }
}
