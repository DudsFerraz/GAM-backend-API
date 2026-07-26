package br.org.gam.api.member.solicitation.persistence;

import br.org.gam.api.member.solicitation.domain.MembershipSolicitationStatus;
import br.org.gam.api.shared.persistence.BaseRepository;
import br.org.gam.api.shared.persistence.ReadOnlySpecificationExecutor;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipSolicitationRepository
        extends BaseRepository<MembershipSolicitationEntity, UUID>,
                ReadOnlySpecificationExecutor<MembershipSolicitationEntity> {

    boolean existsByAccount_IdAndStatus(UUID accountId, MembershipSolicitationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select solicitation
            from MembershipSolicitationEntity solicitation
            join fetch solicitation.account
            left join fetch solicitation.reviewedBy
            left join fetch solicitation.member
            where solicitation.id = :id
            """)
    Optional<MembershipSolicitationEntity> findByIdForUpdate(@Param("id") UUID id);
}
