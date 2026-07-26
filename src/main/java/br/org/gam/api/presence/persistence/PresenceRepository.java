package br.org.gam.api.presence.persistence;

import br.org.gam.api.shared.persistence.BaseRepository;
import br.org.gam.api.shared.persistence.ReadOnlySpecificationExecutor;
import java.util.Optional;
import java.util.UUID;

public interface PresenceRepository extends BaseRepository<PresenceEntity, UUID>,
                                             ReadOnlySpecificationExecutor<PresenceEntity> {
    Optional<PresenceEntity> findByMember_IdAndEvent_Id(UUID memberId, UUID eventId);
    boolean existsByMember_IdAndEvent_Id(UUID memberId, UUID eventId);
    long countByEvent_Id(UUID eventId);
}
