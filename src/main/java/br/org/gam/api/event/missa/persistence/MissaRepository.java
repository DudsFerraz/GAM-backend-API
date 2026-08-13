package br.org.gam.api.event.missa.persistence;

import br.org.gam.api.shared.persistence.BaseRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MissaRepository extends BaseRepository<MissaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select missa from MissaEntity missa where missa.id = :id")
    Optional<MissaEntity> findActiveByIdForUpdate(@Param("id") UUID id);
}
