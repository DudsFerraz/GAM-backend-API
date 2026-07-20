package br.org.gam.api.member.persistence;

import br.org.gam.api.shared.persistence.BaseRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface MemberRepository extends BaseRepository<MemberEntity, UUID>,
                                           JpaSpecificationExecutor<MemberEntity> {

    boolean existsByAccountId(UUID accountId);

    Optional<MemberEntity> findByAccount_Id(UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select member from MemberEntity member where member.id = :id")
    Optional<MemberEntity> findByIdForUpdate(@Param("id") UUID id);
}
