package br.org.gam.api.event.oratorio.attendance.persistence;

import br.org.gam.api.shared.persistence.BaseRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OratorianoAttendanceRepository
        extends BaseRepository<OratorianoAttendanceEntity, UUID> {
    Optional<OratorianoAttendanceEntity> findByOratorio_IdAndOratoriano_Id(
            UUID oratorioId,
            UUID oratorianoId
    );

    long countByOratorio_Id(UUID oratorioId);

    @Query("""
            select attendance.oratoriano.id as oratorianoId,
                   attendance.oratorio.localDate as localDate
            from OratorianoAttendanceEntity attendance
            where attendance.oratoriano.id in :oratorianoIds
            """)
    List<AttendanceDateProjection> findActiveAttendanceDates(
            @Param("oratorianoIds") Set<UUID> oratorianoIds
    );

    interface AttendanceDateProjection {
        UUID getOratorianoId();

        LocalDate getLocalDate();
    }
}
