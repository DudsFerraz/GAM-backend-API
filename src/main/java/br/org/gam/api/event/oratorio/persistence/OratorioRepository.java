package br.org.gam.api.event.oratorio.persistence;

import br.org.gam.api.shared.persistence.BaseRepository;
import java.util.UUID;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface OratorioRepository extends BaseRepository<OratorioEntity, UUID> {
    boolean existsByLocalDate(LocalDate localDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select oratorio from OratorioEntity oratorio where oratorio.id = :id")
    Optional<OratorioEntity> findActiveByIdForUpdate(@Param("id") UUID id);
}
