package br.org.gam.api.oratoriano.application;

import br.org.gam.api.event.oratorio.attendance.persistence.OratorianoAttendanceRepository;
import br.org.gam.api.oratoriano.domain.Oratoriano;
import br.org.gam.api.oratoriano.persistence.OratorianoEntity;
import br.org.gam.api.oratoriano.persistence.OratorianoRepository;
import br.org.gam.api.shared.exception.NotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class OratorianoDomainLoader {

    private final OratorianoRepository oratorianoRepo;
    private final OratorianoMapper oratorianoMapper;
    private final OratorianoAttendanceRepository attendanceRepository;

    public OratorianoDomainLoader(
            OratorianoRepository oratorianoRepo,
            OratorianoMapper oratorianoMapper,
            OratorianoAttendanceRepository attendanceRepository
    ) {
        this.oratorianoRepo = oratorianoRepo;
        this.oratorianoMapper = oratorianoMapper;
        this.attendanceRepository = attendanceRepository;
    }

    public Oratoriano requiredById(UUID id) {
        Oratoriano oratoriano = oratorianoRepo.findById(id)
                .map(oratorianoMapper::entityToDomain)
                .orElseThrow(() -> NotFoundException.resource("Oratoriano", id));
        return oratoriano.withActiveOratorioAttendances(
                activeAttendanceDates(Set.of(id)).getOrDefault(id, List.of())
        );
    }

    public Set<Oratoriano> requiredByIds(Set<UUID> ids) {
        Map<UUID, List<LocalDate>> attendanceDates = activeAttendanceDates(ids);
        return oratorianoRepo.findAllById(ids).stream()
                .map(entity -> loadedDomain(entity, attendanceDates))
                .collect(Collectors.toSet());
    }

    private Oratoriano loadedDomain(
            OratorianoEntity entity,
            Map<UUID, List<LocalDate>> attendanceDates
    ) {
        return oratorianoMapper.entityToDomain(entity)
                .withActiveOratorioAttendances(
                        attendanceDates.getOrDefault(entity.getId(), List.of())
                );
    }

    private Map<UUID, List<LocalDate>> activeAttendanceDates(Set<UUID> oratorianoIds) {
        if (oratorianoIds.isEmpty()) {
            return Map.of();
        }
        return attendanceRepository.findActiveAttendanceDates(oratorianoIds).stream()
                .collect(Collectors.groupingBy(
                        OratorianoAttendanceRepository.AttendanceDateProjection::getOratorianoId,
                        Collectors.mapping(
                                OratorianoAttendanceRepository.AttendanceDateProjection::getLocalDate,
                                Collectors.toList()
                        )
                ));
    }
}
