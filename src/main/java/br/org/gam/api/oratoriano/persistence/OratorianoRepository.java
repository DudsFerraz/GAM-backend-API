package br.org.gam.api.oratoriano.persistence;

import br.org.gam.api.shared.persistence.BaseRepository;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface OratorianoRepository extends BaseRepository<OratorianoEntity, UUID>,
                                              JpaSpecificationExecutor<OratorianoEntity> {
    Optional<OratorianoEntity> findByNameKey(String nameKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select oratoriano from OratorianoEntity oratoriano where oratoriano.id = :id")
    Optional<OratorianoEntity> findActiveByIdForUpdate(@Param("id") UUID id);
}
