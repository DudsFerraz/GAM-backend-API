package br.org.gam.api.event.persistence;

import br.org.gam.api.shared.persistence.BaseRepository;
import br.org.gam.api.shared.persistence.ReadOnlySpecificationExecutor;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends BaseRepository<EventEntity, UUID>,
                                          ReadOnlySpecificationExecutor<EventEntity> {
    @Query(value = "select event.* from events event "
            + "where event.id = :id and event.deleted_at is null for update", nativeQuery = true)
    Optional<EventEntity> findActiveByIdForUpdate(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update events set status = cast(:targetStatus as event_status_enum), "
            + "cancellation_reason = :cancellationReason, updated_at = current_timestamp "
            + "where id = :id and deleted_at is null "
            + "and status = cast(:expectedStatus as event_status_enum)", nativeQuery = true)
    int updateStatusIfCurrent(
            @Param("id") UUID id,
            @Param("expectedStatus") String expectedStatus,
            @Param("targetStatus") String targetStatus,
            @Param("cancellationReason") String cancellationReason
    );
}
