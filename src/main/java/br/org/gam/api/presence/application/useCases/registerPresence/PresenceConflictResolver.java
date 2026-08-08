package br.org.gam.api.presence.application.useCases.registerPresence;

import br.org.gam.api.presence.persistence.PresenceEntity;
import br.org.gam.api.presence.persistence.PresenceRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PresenceConflictResolver {
    private final PresenceRepository presenceRepository;

    public PresenceConflictResolver(PresenceRepository presenceRepository) {
        this.presenceRepository = presenceRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<UUID> findWinningPresenceId(UUID memberId, UUID eventId) {
        return presenceRepository.findByMember_IdAndEvent_Id(memberId, eventId)
                .map(PresenceEntity::getId);
    }
}
