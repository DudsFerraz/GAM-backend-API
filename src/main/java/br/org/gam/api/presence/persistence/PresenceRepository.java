package br.org.gam.api.presence.persistence;

import br.org.gam.api.shared.persistence.BaseRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PresenceRepository extends BaseRepository<PresenceEntity, UUID>,
                                             JpaSpecificationExecutor<PresenceEntity> {
    Optional<PresenceEntity> findByMember_IdAndEvent_Id(UUID memberId, UUID eventId);
    boolean existsByMember_IdAndEvent_Id(UUID memberId, UUID eventId);
}
