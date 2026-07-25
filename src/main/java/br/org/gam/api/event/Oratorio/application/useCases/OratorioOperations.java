package br.org.gam.api.event.oratorio.application.useCases;

import br.org.gam.api.event.application.EventMapper;
import br.org.gam.api.event.application.EventSecurity;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.event.oratorio.application.OratorioApiModels.AttendancePersonRDTO;
import br.org.gam.api.event.oratorio.application.OratorioApiModels.AttendanceRDTO;
import br.org.gam.api.event.oratorio.application.OratorioApiModels.CreateOratorioDTO;
import br.org.gam.api.event.oratorio.application.OratorioApiModels.OratorioRDTO;
import br.org.gam.api.event.oratorio.application.OratorioApiModels.PlanningDTO;
import br.org.gam.api.event.oratorio.application.OratorioApiModels.PresentSummaryRDTO;
import br.org.gam.api.event.oratorio.application.OratorioApiModels.QuickRegistrationRDTO;
import br.org.gam.api.event.oratorio.application.OratorioApiModels.RosterEntryRDTO;
import br.org.gam.api.event.oratorio.application.OratorioApiModels.ScheduleItemRDTO;
import br.org.gam.api.event.oratorio.application.OratorioApiModels.TeamMemberRDTO;
import br.org.gam.api.event.oratorio.application.OratorioApiModels.TeamRDTO;
import br.org.gam.api.event.oratorio.application.OratorioApiModels.TeamType;
import br.org.gam.api.event.oratorio.attendance.persistence.OratorianoAttendanceEntity;
import br.org.gam.api.event.oratorio.attendance.persistence.OratorianoAttendanceRepository;
import br.org.gam.api.event.oratorio.persistence.OratorioEntity;
import br.org.gam.api.event.oratorio.persistence.OratorioRepository;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.event.persistence.EventRepository;
import br.org.gam.api.gamLocation.application.GamLocationNormalizer;
import br.org.gam.api.gamLocation.persistence.GamLocationEntity;
import br.org.gam.api.gamLocation.persistence.GamLocationRepository;
import br.org.gam.api.member.application.MemberEntityLoader;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.oratoriano.application.OratorianoApiModels.RegisterOratorianoDTO;
import br.org.gam.api.oratoriano.application.useCases.OratorianoRecords;
import br.org.gam.api.oratoriano.persistence.OratorianoEntity;
import br.org.gam.api.oratoriano.persistence.OratorianoRepository;
import br.org.gam.api.presence.persistence.PresenceEntity;
import br.org.gam.api.presence.persistence.PresenceRepository;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.rbac.permission.persistence.PermissionEntity;
import br.org.gam.api.rbac.permission.persistence.PermissionRepository;
import br.org.gam.api.shared.activitylog.ActivityAction;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.activitylog.ActivityTargetType;
import br.org.gam.api.shared.exception.ConflictException;
import br.org.gam.api.shared.exception.InvalidCommandException;
import br.org.gam.api.shared.exception.NotFoundException;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import br.org.gam.api.shared.validation.RequiredReason;
import br.org.gam.api.shared.web.PagedResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.AuditorAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OratorioOperations {
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final LocalTime START = LocalTime.of(14, 0);
    private static final LocalTime END = LocalTime.of(17, 0);

    private final OratorioRepository oratorioRepository;
    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final EventSecurity eventSecurity;
    private final GamLocationRepository locationRepository;
    private final PermissionRepository permissionRepository;
    private final MemberEntityLoader memberLoader;
    private final OratorianoRepository oratorianoRepository;
    private final PresenceRepository presenceRepository;
    private final OratorianoAttendanceRepository attendanceRepository;
    private final OratorianoRecords oratorianoRecords;
    private final JdbcTemplate jdbcTemplate;
    private final AuditorAware<UUID> auditorAware;
    private final ActivityEvents activityEvents;
    private final Clock clock;
    private final String configuredLocationIdentity;

    public OratorioOperations(
            OratorioRepository oratorioRepository,
            EventRepository eventRepository,
            EventMapper eventMapper,
            EventSecurity eventSecurity,
            GamLocationRepository locationRepository,
            PermissionRepository permissionRepository,
            MemberEntityLoader memberLoader,
            OratorianoRepository oratorianoRepository,
            PresenceRepository presenceRepository,
            OratorianoAttendanceRepository attendanceRepository,
            OratorianoRecords oratorianoRecords,
            JdbcTemplate jdbcTemplate,
            AuditorAware<UUID> auditorAware,
            ActivityEvents activityEvents,
            Clock clock,
            @Value("${gam.oratorio.location-name}") String configuredLocationName
    ) {
        this.oratorioRepository = oratorioRepository;
        this.eventRepository = eventRepository;
        this.eventMapper = eventMapper;
        this.eventSecurity = eventSecurity;
        this.locationRepository = locationRepository;
        this.permissionRepository = permissionRepository;
        this.memberLoader = memberLoader;
        this.oratorianoRepository = oratorianoRepository;
        this.presenceRepository = presenceRepository;
        this.attendanceRepository = attendanceRepository;
        this.oratorianoRecords = oratorianoRecords;
        this.jdbcTemplate = jdbcTemplate;
        this.auditorAware = auditorAware;
        this.activityEvents = activityEvents;
        this.clock = clock;
        this.configuredLocationIdentity = GamLocationNormalizer.canonical(configuredLocationName);
    }

    @Transactional
    public OratorioRDTO create(CreateOratorioDTO dto) {
        LocalDate date = dto.date();
        if (oratorioRepository.existsByLocalDate(date)) {
            throw duplicateDate(date);
        }
        GamLocationEntity location = locationRepository.findActiveByIdentityNameForUpdate(
                        configuredLocationIdentity
                )
                .orElseThrow(() -> ConflictException.resource(
                        "ORATORIO_LOCATION_UNAVAILABLE",
                        "GamLocation",
                        configuredLocationIdentity,
                        "The configured active São Mário GamLocation is unavailable."
                ));
        PermissionEntity audience = permissionRepository.findByCode(PermissionEnum.Code.EVENT_GET_MEMBER)
                .orElseThrow(() -> NotFoundException.resource(
                        "Permission",
                        PermissionEnum.Code.EVENT_GET_MEMBER
                ));
        Instant begin = ZonedDateTime.of(date, START, SAO_PAULO).toInstant();
        Instant end = ZonedDateTime.of(date, END, SAO_PAULO).toInstant();
        Instant now = clock.instant();
        UUID id = UUIDGenerator.generateUUIDV7();

        EventEntity event = new EventEntity();
        event.setId(id);
        event.setTitle("Oratório");
        event.setDescription("");
        event.setLocation(location);
        event.setRequiredPermission(audience);
        event.setType(EventType.ORATORIO);
        event.setStatus(end.isAfter(now) ? EventStatus.SCHEDULED : EventStatus.COMPLETED);
        event.setBeginDate(begin);
        event.setEndDate(end);

        OratorioEntity oratorio = new OratorioEntity();
        oratorio.setId(id);
        oratorio.setEvent(event);
        oratorio.setLocalDate(date);
        try {
            eventRepository.save(event);
            oratorioRepository.saveAndFlush(oratorio);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateDate(date);
        }
        activityEvents.oratorioCreated(id, id);
        return detail(oratorio);
    }

    @Transactional(readOnly = true)
    public OratorioRDTO get(UUID id) {
        OratorioEntity oratorio = oratorioRepository.findById(id)
                .orElseThrow(() -> NotFoundException.resource("Oratorio", id));
        if (!eventSecurity.canGetEvent(oratorio.getEvent())) {
            throw NotFoundException.resource("Oratorio", id);
        }
        return detail(oratorio);
    }

    @Transactional
    public OratorioRDTO replacePlanning(UUID id, PlanningDTO dto) {
        OratorioEntity oratorio = requiredForUpdate(id);
        EventStatus status = effectiveStatus(oratorio.getEvent());
        if (status == EventStatus.FINALIZED || status == EventStatus.CANCELLED) {
            throw lifecycleConflict(id, status, "Planning is closed for this occurrence.");
        }
        PlanningDTO normalized = new PlanningDTO(
                normalizePlanning(dto.lancheDescription()),
                normalizePlanning(dto.gincanaDescription()),
                normalizePlanning(dto.boaTardeCriancasPlan()),
                normalizePlanning(dto.boaTardeJovensPlan())
        );
        PlanningDTO previous = planning(oratorio);
        if (Objects.equals(previous, normalized)) {
            return detail(oratorio);
        }
        oratorio.setLancheDescription(normalized.lancheDescription());
        oratorio.setGincanaDescription(normalized.gincanaDescription());
        oratorio.setBoaTardeCriancasPlan(normalized.boaTardeCriancasPlan());
        oratorio.setBoaTardeJovensPlan(normalized.boaTardeJovensPlan());
        oratorioRepository.save(oratorio);
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(previous.lancheDescription(), normalized.lancheDescription())) {
            changed.add("lancheDescription");
        }
        if (!Objects.equals(previous.gincanaDescription(), normalized.gincanaDescription())) {
            changed.add("gincanaDescription");
        }
        if (!Objects.equals(previous.boaTardeCriancasPlan(), normalized.boaTardeCriancasPlan())) {
            changed.add("boaTardeCriancasPlan");
        }
        if (!Objects.equals(previous.boaTardeJovensPlan(), normalized.boaTardeJovensPlan())) {
            changed.add("boaTardeJovensPlan");
        }
        moduleActivity(
                ActivityAction.ORATORIO_PLANNING_UPDATED,
                id,
                null,
                "Oratorio planning replaced",
                Map.of("oratorioId", id, "changedFields", List.copyOf(changed))
        );
        return detail(oratorio);
    }

    @Transactional
    public void assignTeamMember(UUID id, TeamType teamType, UUID memberId) {
        OratorioEntity oratorio = requiredForUpdate(id);
        assertPlanningMutable(oratorio);
        MemberEntity member = memberLoader.requiredById(memberId);
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw ConflictException.resource(
                    "ORATORIO_TEAM_MEMBER_INACTIVE",
                    "Member",
                    memberId,
                    "Only an active Member may receive a new Oratorio team assignment."
            );
        }
        int changed = jdbcTemplate.update(
                "INSERT INTO oratorio_team_assignments "
                        + "(oratorio_id, member_id, team_type, created_at, created_by) "
                        + "VALUES (?, ?, ?::oratorio_team_type_enum, ?, ?) ON CONFLICT DO NOTHING",
                id,
                memberId,
                teamType.name(),
                Timestamp.from(clock.instant()),
                auditorAware.getCurrentAuditor().orElse(null)
        );
        if (changed > 0) {
            moduleActivity(
                    ActivityAction.ORATORIO_TEAM_MEMBER_ASSIGNED,
                    id,
                    null,
                    "Member assigned to Oratorio team",
                    Map.of("oratorioId", id, "memberId", memberId, "teamType", teamType.name())
            );
        }
    }

    @Transactional
    public void removeTeamMember(UUID id, TeamType teamType, UUID memberId) {
        OratorioEntity oratorio = requiredForUpdate(id);
        assertPlanningMutable(oratorio);
        int changed = jdbcTemplate.update(
                "DELETE FROM oratorio_team_assignments "
                        + "WHERE oratorio_id = ? AND member_id = ? AND team_type = ?::oratorio_team_type_enum",
                id,
                memberId,
                teamType.name()
        );
        if (changed > 0) {
            moduleActivity(
                    ActivityAction.ORATORIO_TEAM_MEMBER_REMOVED,
                    id,
                    null,
                    "Member removed from Oratorio team",
                    Map.of("oratorioId", id, "memberId", memberId, "teamType", teamType.name())
            );
        }
    }

    @Transactional
    public void lock(UUID id) {
        transition(id, EventStatus.LOCKED, null, EventStatus.COMPLETED);
    }

    @Transactional
    public void finalizeOccurrence(UUID id) {
        transition(id, EventStatus.FINALIZED, null, EventStatus.COMPLETED, EventStatus.LOCKED);
    }

    @Transactional
    public void cancel(UUID id, String rawReason) {
        String reason = RequiredReason.normalize(rawReason, "Oratorio cancellation requires an audit reason.");
        transition(id, EventStatus.CANCELLED, reason, EventStatus.SCHEDULED);
    }

    @Transactional
    public void reopen(UUID id, EventStatus targetStatus, String rawReason) {
        String reason = RequiredReason.normalize(rawReason, "Oratorio reopening requires an audit reason.");
        if (targetStatus == EventStatus.COMPLETED) {
            transition(id, targetStatus, reason, EventStatus.LOCKED, EventStatus.FINALIZED);
            return;
        }
        if (targetStatus == EventStatus.LOCKED) {
            transition(id, targetStatus, reason, EventStatus.FINALIZED);
            return;
        }
        throw InvalidCommandException.reason("Reopening targetStatus must be LOCKED or COMPLETED.");
    }

    @Transactional
    public void delete(UUID id, String rawReason) {
        String reason = RequiredReason.normalize(rawReason, "Oratorio deletion requires an audit reason.");
        OratorioEntity oratorio = requiredForUpdate(id);
        EventEntity event = eventRepository.findActiveByIdForUpdate(id)
                .orElseThrow(() -> NotFoundException.resource("Oratorio", id));
        EventStatus status = effectiveStatus(event);
        if (status == EventStatus.LOCKED || status == EventStatus.FINALIZED) {
            throw lifecycleConflict(id, status, "The occurrence must be reopened before deletion.");
        }
        long memberAttendance = presenceRepository.countByEvent_Id(id);
        long oratorianoAttendance = attendanceRepository.countByOratorio_Id(id);
        if (memberAttendance > 0 || oratorianoAttendance > 0) {
            throw ConflictException.resource(
                    "ORATORIO_HAS_ACTIVE_ATTENDANCE",
                    "Oratorio",
                    id,
                    "Active attendance must be removed before deleting the occurrence."
            );
        }
        oratorioRepository.delete(oratorio);
        eventRepository.delete(event);
        moduleActivity(
                ActivityAction.ORATORIO_DELETED,
                id,
                reason,
                "Oratorio occurrence deleted",
                Map.of("oratorioId", id, "status", status.name())
        );
    }

    @Transactional
    public AttendanceMutation markMember(UUID oratorioId, UUID memberId) {
        OratorioEntity occurrence = attendanceOccurrence(oratorioId, true);
        return presenceRepository.findByMember_IdAndEvent_Id(memberId, oratorioId)
                .map(existing -> new AttendanceMutation(false, memberAttendance(existing)))
                .orElseGet(() -> {
                    MemberEntity member = memberLoader.requiredById(memberId);
                    PresenceEntity presence = new PresenceEntity();
                    presence.setId(UUIDGenerator.generateUUIDV7());
                    presence.setMember(member);
                    presence.setEvent(occurrence.getEvent());
                    presence.setObservations(null);
                    presenceRepository.saveAndFlush(presence);
                    activityEvents.moduleActivity(
                            ActivityAction.ORATORIO_MEMBER_ATTENDANCE_REGISTERED,
                            ActivityTargetType.PRESENCE,
                            presence.getId(),
                            null,
                            "Member marked present in Oratorio tracker",
                            Map.of("oratorioId", oratorioId, "memberId", memberId)
                    );
                    return new AttendanceMutation(true, memberAttendance(presence));
                });
    }

    @Transactional
    public AttendanceMutation markOratoriano(UUID oratorioId, UUID oratorianoId) {
        OratorioEntity occurrence = attendanceOccurrence(oratorioId, true);
        return attendanceRepository.findByOratorio_IdAndOratoriano_Id(oratorioId, oratorianoId)
                .map(existing -> new AttendanceMutation(false, oratorianoAttendance(existing)))
                .orElseGet(() -> {
                    OratorianoEntity oratoriano = activeOratorianoForAttendance(oratorianoId);
                    OratorianoAttendanceEntity attendance = new OratorianoAttendanceEntity();
                    attendance.setId(UUIDGenerator.generateUUIDV7());
                    attendance.setOratorio(occurrence);
                    attendance.setOratoriano(oratoriano);
                    attendance.setRegisteredAt(clock.instant());
                    attendanceRepository.saveAndFlush(attendance);
                    activityEvents.moduleActivity(
                            ActivityAction.ORATORIANO_ATTENDANCE_REGISTERED,
                            ActivityTargetType.ORATORIANO_ATTENDANCE,
                            attendance.getId(),
                            null,
                            "Oratoriano marked present",
                            Map.of("oratorioId", oratorioId, "oratorianoId", oratorianoId)
                    );
                    return new AttendanceMutation(true, oratorianoAttendance(attendance));
                });
    }

    @Transactional
    public void uncheckMember(UUID oratorioId, UUID memberId, String rawReason) {
        EventStatus status = attendanceMutationStatus(oratorioId);
        String reason = removalReason(status, rawReason);
        presenceRepository.findByMember_IdAndEvent_Id(memberId, oratorioId).ifPresent(presence -> {
            presenceRepository.delete(presence);
            activityEvents.moduleActivity(
                    ActivityAction.ORATORIO_MEMBER_ATTENDANCE_REMOVED,
                    ActivityTargetType.PRESENCE,
                    presence.getId(),
                    reason,
                    "Member attendance removed from Oratorio tracker",
                    Map.of("oratorioId", oratorioId, "memberId", memberId)
            );
        });
    }

    @Transactional
    public void uncheckOratoriano(UUID oratorioId, UUID oratorianoId, String rawReason) {
        EventStatus status = attendanceMutationStatus(oratorioId);
        String reason = removalReason(status, rawReason);
        attendanceRepository.findByOratorio_IdAndOratoriano_Id(oratorioId, oratorianoId).ifPresent(attendance -> {
            attendanceRepository.delete(attendance);
            activityEvents.moduleActivity(
                    ActivityAction.ORATORIANO_ATTENDANCE_REMOVED,
                    ActivityTargetType.ORATORIANO_ATTENDANCE,
                    attendance.getId(),
                    reason,
                    "Oratoriano attendance removed",
                    Map.of("oratorioId", oratorioId, "oratorianoId", oratorianoId)
            );
        });
    }

    @Transactional
    public QuickRegistrationRDTO registerAndMark(UUID oratorioId, RegisterOratorianoDTO dto) {
        OratorioEntity occurrence = attendanceOccurrence(oratorioId, true);
        var oratorianoRDTO = oratorianoRecords.registerWithoutActivity(dto);
        OratorianoEntity oratoriano = oratorianoRepository.findById(oratorianoRDTO.id())
                .orElseThrow(() -> NotFoundException.resource("Oratoriano", oratorianoRDTO.id()));
        OratorianoAttendanceEntity attendance = new OratorianoAttendanceEntity();
        attendance.setId(UUIDGenerator.generateUUIDV7());
        attendance.setOratorio(occurrence);
        attendance.setOratoriano(oratoriano);
        attendance.setRegisteredAt(clock.instant());
        attendanceRepository.saveAndFlush(attendance);
        activityEvents.moduleActivity(
                ActivityAction.ORATORIANO_REGISTERED_AND_MARKED_PRESENT,
                ActivityTargetType.ORATORIANO_ATTENDANCE,
                attendance.getId(),
                null,
                "Oratoriano registered and marked present",
                Map.of(
                        "oratorioId", oratorioId,
                        "oratorianoId", oratoriano.getId(),
                        "attendanceId", attendance.getId()
                )
        );
        return new QuickRegistrationRDTO(oratorianoRDTO, oratorianoAttendance(attendance));
    }

    @Transactional(readOnly = true)
    public PagedResponse<RosterEntryRDTO> memberRoster(UUID oratorioId, int page, String name) {
        required(oratorioId);
        int safePage = Math.max(page, 0);
        String search = name == null ? "" : name;
        List<RosterEntryRDTO> items = jdbcTemplate.query(
                "SELECT m.id, m.first_name, m.surname, m.status::text, "
                        + "p.id AS attendance_id, p.created_at AS registered_at "
                        + "FROM members m "
                        + "LEFT JOIN presences p ON p.member_id = m.id AND p.event_id = ? AND p.deleted_at IS NULL "
                        + "WHERE m.deleted_at IS NULL AND m.status = 'ACTIVE' "
                        + "AND regexp_replace(normalize(lower(m.first_name || ' ' || m.surname), NFD), "
                        + "U&'[\\0300-\\036F]', '', 'g') LIKE '%' || "
                        + "regexp_replace(normalize(lower(?), NFD), U&'[\\0300-\\036F]', '', 'g') || '%' "
                        + "ORDER BY m.first_name, m.surname, m.id LIMIT 50 OFFSET ?",
                (rs, rowNum) -> {
                    AttendancePersonRDTO person = new AttendancePersonRDTO(
                            rs.getObject("id", UUID.class),
                            rs.getString("first_name"),
                            rs.getString("surname"),
                            rs.getString("status"),
                            false
                    );
                    UUID attendanceId = rs.getObject("attendance_id", UUID.class);
                    AttendanceRDTO attendance = attendanceId == null
                            ? null
                            : new AttendanceRDTO(
                                    attendanceId,
                                    person,
                                    rs.getTimestamp("registered_at").toInstant()
                            );
                    return new RosterEntryRDTO(person, attendance);
                },
                oratorioId,
                search,
                safePage * 50
        );
        long total = rosterCount("members", search);
        return page(items, safePage, total);
    }

    @Transactional(readOnly = true)
    public PagedResponse<RosterEntryRDTO> oratorianoRoster(UUID oratorioId, int page, String name) {
        required(oratorioId);
        int safePage = Math.max(page, 0);
        String search = name == null ? "" : name;
        List<RosterEntryRDTO> items = jdbcTemplate.query(
                "SELECT o.id, o.first_name, o.surname, a.id AS attendance_id, a.registered_at "
                        + "FROM oratorianos o "
                        + "LEFT JOIN oratoriano_attendances a ON a.oratoriano_id = o.id "
                        + "AND a.oratorio_id = ? AND a.deleted_at IS NULL "
                        + "WHERE o.deleted_at IS NULL AND o.name_key LIKE '%' || "
                        + "regexp_replace(normalize(lower(?), NFD), U&'[\\0300-\\036F]', '', 'g') || '%' "
                        + "ORDER BY o.name_key, o.id LIMIT 50 OFFSET ?",
                (rs, rowNum) -> {
                    AttendancePersonRDTO person = new AttendancePersonRDTO(
                            rs.getObject("id", UUID.class),
                            rs.getString("first_name"),
                            rs.getString("surname"),
                            "REGISTERED",
                            false
                    );
                    UUID attendanceId = rs.getObject("attendance_id", UUID.class);
                    AttendanceRDTO attendance = attendanceId == null
                            ? null
                            : new AttendanceRDTO(
                                    attendanceId,
                                    person,
                                    rs.getTimestamp("registered_at").toInstant()
                            );
                    return new RosterEntryRDTO(person, attendance);
                },
                oratorioId,
                search,
                safePage * 50
        );
        long total = rosterCount("oratorianos", search);
        return page(items, safePage, total);
    }

    @Transactional(readOnly = true)
    public PresentSummaryRDTO present(UUID oratorioId) {
        required(oratorioId);
        List<AttendanceRDTO> members = jdbcTemplate.query(
                "SELECT p.id, p.created_at AS registered_at, m.id AS person_id, "
                        + "m.first_name, m.surname, m.status::text, m.deleted_at "
                        + "FROM presences p JOIN members m ON m.id = p.member_id "
                        + "WHERE p.event_id = ? AND p.deleted_at IS NULL "
                        + "ORDER BY m.first_name, m.surname, m.id",
                (rs, rowNum) -> new AttendanceRDTO(
                        rs.getObject("id", UUID.class),
                        new AttendancePersonRDTO(
                                rs.getObject("person_id", UUID.class),
                                rs.getString("first_name"),
                                rs.getString("surname"),
                                rs.getString("status"),
                                rs.getObject("deleted_at") != null
                        ),
                        rs.getTimestamp("registered_at").toInstant()
                ),
                oratorioId
        );
        List<AttendanceRDTO> oratorianos = jdbcTemplate.query(
                "SELECT a.id, a.registered_at, o.id AS person_id, o.first_name, o.surname, o.deleted_at "
                        + "FROM oratoriano_attendances a JOIN oratorianos o ON o.id = a.oratoriano_id "
                        + "WHERE a.oratorio_id = ? AND a.deleted_at IS NULL "
                        + "ORDER BY o.name_key, o.id",
                (rs, rowNum) -> {
                    boolean deleted = rs.getTimestamp("deleted_at") != null;
                    return new AttendanceRDTO(
                            rs.getObject("id", UUID.class),
                            new AttendancePersonRDTO(
                                    rs.getObject("person_id", UUID.class),
                                    rs.getString("first_name"),
                                    rs.getString("surname"),
                                    deleted ? "DELETED" : "REGISTERED",
                                    deleted
                            ),
                            rs.getTimestamp("registered_at").toInstant()
                    );
                },
                oratorioId
        );
        return new PresentSummaryRDTO(members, oratorianos);
    }

    private OratorioEntity attendanceOccurrence(UUID id, boolean addition) {
        EventEntity event = eventRepository.findActiveByIdForUpdate(id)
                .orElseThrow(() -> NotFoundException.resource("Oratorio", id));
        OratorioEntity occurrence = oratorioRepository.findById(id)
                .orElseThrow(() -> NotFoundException.resource("Oratorio", id));
        EventStatus status = effectiveStatus(event);
        if (!additionAllowed(event, status)) {
            throw lifecycleConflict(id, status, "New attendance is not allowed for this occurrence.");
        }
        return occurrence;
    }

    private EventStatus attendanceMutationStatus(UUID id) {
        EventEntity event = eventRepository.findActiveByIdForUpdate(id)
                .orElseThrow(() -> NotFoundException.resource("Oratorio", id));
        required(id);
        EventStatus status = effectiveStatus(event);
        if (status == EventStatus.LOCKED || status == EventStatus.FINALIZED) {
            throw lifecycleConflict(id, status, "Attendance correction is closed for this occurrence.");
        }
        return status;
    }

    private boolean additionAllowed(EventEntity event, EventStatus status) {
        if (status == EventStatus.COMPLETED) {
            return true;
        }
        if (status != EventStatus.SCHEDULED) {
            return false;
        }
        return !clock.instant().isBefore(event.getBeginDate().minusSeconds(30 * 60L));
    }

    private String removalReason(EventStatus status, String rawReason) {
        if (status == EventStatus.COMPLETED) {
            return RequiredReason.normalize(
                    rawReason,
                    "Completed Oratorio attendance removal requires an audit reason."
            );
        }
        if (rawReason == null || rawReason.isBlank()) {
            return null;
        }
        return RequiredReason.normalize(rawReason, "Invalid Oratorio attendance removal reason.");
    }

    private OratorianoEntity activeOratorianoForAttendance(UUID id) {
        return oratorianoRepository.findActiveByIdForUpdate(id).orElseGet(() -> {
            Integer existing = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM oratorianos WHERE id = ?",
                    Integer.class,
                    id
            );
            if (existing != null && existing > 0) {
                throw ConflictException.resource(
                        "ORATORIANO_DELETED",
                        "Oratoriano",
                        id,
                        "A deleted Oratoriano cannot receive new attendance."
                );
            }
            throw NotFoundException.resource("Oratoriano", id);
        });
    }

    private void transition(
            UUID id,
            EventStatus target,
            String reason,
            EventStatus... allowedSources
    ) {
        /*
         * Planning and team mutations serialize on the Oratorio row. Lifecycle
         * transitions must enter through that same boundary before locking the
         * backing Event, otherwise finalization can overtake an in-flight
         * planning/team write that is blocked on a dependent row.
         */
        requiredForUpdate(id);
        EventEntity event = eventRepository.findActiveByIdForUpdate(id)
                .orElseThrow(() -> NotFoundException.resource("Oratorio", id));
        EventStatus current = effectiveStatus(event);
        boolean allowed = false;
        for (EventStatus source : allowedSources) {
            allowed |= current == source;
        }
        if (!allowed) {
            throw lifecycleConflict(id, current, "The requested lifecycle transition is not allowed.");
        }
        event.setStatus(target);
        event.setCancellationReason(target == EventStatus.CANCELLED ? reason : null);
        eventRepository.save(event);
        ActivityAction action = switch (target) {
            case LOCKED -> current == EventStatus.FINALIZED
                    ? ActivityAction.ORATORIO_REOPENED
                    : ActivityAction.ORATORIO_LOCKED;
            case FINALIZED -> ActivityAction.ORATORIO_FINALIZED;
            case CANCELLED -> ActivityAction.ORATORIO_CANCELLED;
            case COMPLETED -> ActivityAction.ORATORIO_REOPENED;
            default -> throw InvalidCommandException.reason("Unsupported Oratorio lifecycle transition.");
        };
        moduleActivity(
                action,
                id,
                reason,
                "Oratorio lifecycle changed",
                Map.of("oratorioId", id, "fromStatus", current.name(), "toStatus", target.name())
        );
    }

    private OratorioRDTO detail(OratorioEntity oratorio) {
        return new OratorioRDTO(
                oratorio.getId(),
                eventMapper.entityToRDTO(oratorio.getEvent(), clock.instant()),
                fixedSchedule(),
                planning(oratorio),
                teams(oratorio.getId())
        );
    }

    private List<ScheduleItemRDTO> fixedSchedule() {
        return List.of(
                new ScheduleItemRDTO("14:00", "15:30", "Recreação livre", false),
                new ScheduleItemRDTO("15:30", "16:30", "Gincana", false),
                new ScheduleItemRDTO(
                        "16:30",
                        "17:00",
                        "Boa Tarde das Crianças and Boa Tarde dos Jovens",
                        false
                ),
                new ScheduleItemRDTO("17:00", null, "Lanche", true)
        );
    }

    private PlanningDTO planning(OratorioEntity oratorio) {
        return new PlanningDTO(
                oratorio.getLancheDescription(),
                oratorio.getGincanaDescription(),
                oratorio.getBoaTardeCriancasPlan(),
                oratorio.getBoaTardeJovensPlan()
        );
    }

    private List<TeamRDTO> teams(UUID oratorioId) {
        EnumMap<TeamType, List<TeamMemberRDTO>> grouped = new EnumMap<>(TeamType.class);
        for (TeamType type : TeamType.values()) {
            grouped.put(type, new ArrayList<>());
        }
        jdbcTemplate.query(
                "SELECT a.team_type::text, m.id, m.first_name, m.surname, m.status::text "
                        + "FROM oratorio_team_assignments a JOIN members m ON m.id = a.member_id "
                        + "WHERE a.oratorio_id = ? ORDER BY a.team_type, m.first_name, m.surname, m.id",
                (ResultSetExtractor<Void>) rs -> {
                    while (rs.next()) {
                        grouped.get(TeamType.valueOf(rs.getString("team_type"))).add(
                                new TeamMemberRDTO(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("first_name"),
                                        rs.getString("surname"),
                                        MemberStatus.valueOf(rs.getString("status"))
                                )
                        );
                    }
                    return null;
                },
                oratorioId
        );
        return grouped.entrySet().stream()
                .map(entry -> new TeamRDTO(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }

    private AttendanceRDTO memberAttendance(PresenceEntity presence) {
        MemberEntity member = presence.getMember();
        return new AttendanceRDTO(
                presence.getId(),
                new AttendancePersonRDTO(
                        member.getId(),
                        member.getName().firstName(),
                        member.getName().surname(),
                        member.getStatus().name(),
                        member.getDeletedAt() != null
                ),
                presence.getCreatedAt() == null ? clock.instant() : presence.getCreatedAt()
        );
    }

    private AttendanceRDTO oratorianoAttendance(OratorianoAttendanceEntity attendance) {
        OratorianoEntity person = attendance.getOratoriano();
        return new AttendanceRDTO(
                attendance.getId(),
                new AttendancePersonRDTO(
                        person.getId(),
                        person.getName().firstName(),
                        person.getName().surname(),
                        person.getDeletedAt() == null ? "REGISTERED" : "DELETED",
                        person.getDeletedAt() != null
                ),
                attendance.getRegisteredAt()
        );
    }

    private OratorioEntity required(UUID id) {
        return oratorioRepository.findById(id)
                .orElseThrow(() -> NotFoundException.resource("Oratorio", id));
    }

    private OratorioEntity requiredForUpdate(UUID id) {
        return oratorioRepository.findActiveByIdForUpdate(id)
                .orElseThrow(() -> NotFoundException.resource("Oratorio", id));
    }

    private void assertPlanningMutable(OratorioEntity oratorio) {
        EventStatus status = effectiveStatus(oratorio.getEvent());
        if (status == EventStatus.FINALIZED || status == EventStatus.CANCELLED) {
            throw lifecycleConflict(oratorio.getId(), status, "Planning is closed for this occurrence.");
        }
    }

    private EventStatus effectiveStatus(EventEntity event) {
        return Event.effectiveStatus(event.getStatus(), event.getEndDate(), clock.instant());
    }

    private String normalizePlanning(String value) {
        if (value == null) return null;
        String normalized = value.strip();
        if (normalized.isEmpty()) return null;
        if (normalized.codePointCount(0, normalized.length()) > 10_000) {
            throw InvalidCommandException.reason("Oratorio planning text must contain at most 10000 characters.");
        }
        return normalized;
    }

    private long rosterCount(String table, String search) {
        String sql;
        if ("members".equals(table)) {
            sql = "SELECT COUNT(*) FROM members m WHERE m.deleted_at IS NULL AND m.status = 'ACTIVE' "
                    + "AND regexp_replace(normalize(lower(m.first_name || ' ' || m.surname), NFD), "
                    + "U&'[\\0300-\\036F]', '', 'g') LIKE '%' || "
                    + "regexp_replace(normalize(lower(?), NFD), U&'[\\0300-\\036F]', '', 'g') || '%'";
        } else {
            sql = "SELECT COUNT(*) FROM oratorianos o WHERE o.deleted_at IS NULL "
                    + "AND o.name_key LIKE '%' || regexp_replace(normalize(lower(?), NFD), "
                    + "U&'[\\0300-\\036F]', '', 'g') || '%'";
        }
        Long result = jdbcTemplate.queryForObject(sql, Long.class, search);
        return result == null ? 0 : result;
    }

    private PagedResponse<RosterEntryRDTO> page(List<RosterEntryRDTO> items, int page, long total) {
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / 50.0);
        return new PagedResponse<>(
                items,
                page,
                50,
                total,
                totalPages,
                page == 0,
                totalPages == 0 || page >= totalPages - 1
        );
    }

    private ConflictException duplicateDate(LocalDate date) {
        return ConflictException.resource(
                "ORATORIO_DATE_ALREADY_EXISTS",
                "Oratorio",
                date,
                "An active Oratorio occurrence already exists for this local date."
        );
    }

    private ConflictException lifecycleConflict(UUID id, EventStatus status, String message) {
        return ConflictException.resource(
                "ORATORIO_LIFECYCLE_CONFLICT",
                "Oratorio",
                id,
                message,
                Map.of("oratorioId", id, "status", status.name())
        );
    }

    private void moduleActivity(
            ActivityAction action,
            UUID id,
            String reason,
            String summary,
            Map<String, Object> metadata
    ) {
        activityEvents.moduleActivity(
                action,
                ActivityTargetType.ORATORIO,
                id,
                reason,
                summary,
                metadata
        );
    }

    public record AttendanceMutation(boolean created, AttendanceRDTO attendance) {
    }
}
