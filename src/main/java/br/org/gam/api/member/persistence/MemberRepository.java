package br.org.gam.api.member.persistence;

import br.org.gam.api.shared.persistence.BaseRepository;
import br.org.gam.api.shared.persistence.ReadOnlySpecificationExecutor;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface MemberRepository extends BaseRepository<MemberEntity, UUID>,
                                           ReadOnlySpecificationExecutor<MemberEntity> {

    boolean existsByAccountId(UUID accountId);

    Optional<MemberEntity> findByAccount_Id(UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select member from MemberEntity member where member.id = :id")
    Optional<MemberEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("select (count(member) > 0) from MemberEntity member "
            + "where lower(concat(member.name.firstName, ' ', member.name.surname)) = lower(:fullName) "
            + "and member.id <> :excludedId")
    boolean existsDifferentByCanonicalFullName(@Param("fullName") String fullName,
                                               @Param("excludedId") UUID excludedId);

    @Query(value = "select count(*) from activity_logs where target_id = :batchId "
            + "and action = 'MEMBER_INFORMATION_IMPORTED'", nativeQuery = true)
    long countImportActivities(@Param("batchId") UUID batchId);
}
