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

    @Query(value = "select exists(select 1 from member_information_import_batches where id = :batchId)",
            nativeQuery = true)
    boolean importBatchExists(@Param("batchId") UUID batchId);

    @Query(value = "select exists(select 1 from members where id = :id)", nativeQuery = true)
    boolean identifierExistsIncludingSoftDeleted(@Param("id") UUID id);

    @Query(value = """
            select case when count(*) = 1 and count(*) filter (where
                    activity.actor_kind = 'DEVELOPER'
                    and activity.actor_account_id is null
                    and activity.actor_reference is not null
                    and btrim(activity.actor_reference) <> ''
                    and activity.target_type = 'MEMBER_INFORMATION_IMPORT_BATCH'
                    and activity.target_id = :batchId
                    and activity.target_scope is null
                    and activity.action = 'MEMBER_INFORMATION_IMPORTED'
                    and activity.reason = batch.reason
                    and (activity.metadata - 'surveyCycle' - 'memberCount' - 'responseCount') = '{}'::jsonb
                    and jsonb_typeof(activity.metadata -> 'surveyCycle') = 'number'
                    and jsonb_typeof(activity.metadata -> 'memberCount') = 'number'
                    and jsonb_typeof(activity.metadata -> 'responseCount') = 'number'
                    and (activity.metadata ->> 'surveyCycle') = batch.survey_cycle::text
                    and (activity.metadata ->> 'memberCount') = batch.imported_member_count::text
                    and (activity.metadata ->> 'responseCount') = batch.imported_response_count::text
                    and activity.request_id is null
            ) = 1 then 1 else 0 end
            from activity_logs activity
            join member_information_import_batches batch on batch.id = :batchId
            where activity.target_id = :batchId
              and activity.action = 'MEMBER_INFORMATION_IMPORTED'
            """, nativeQuery = true)
    long countImportActivities(@Param("batchId") UUID batchId);

}
