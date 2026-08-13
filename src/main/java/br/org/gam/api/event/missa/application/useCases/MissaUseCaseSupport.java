package br.org.gam.api.event.missa.application.useCases;

import br.org.gam.api.event.application.EventMapper;
import br.org.gam.api.event.application.EventSecurity;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.event.missa.application.MissaApiModels.AssignedMemberRDTO;
import br.org.gam.api.event.missa.application.MissaApiModels.CreateMissaDTO;
import br.org.gam.api.event.missa.application.MissaApiModels.MissaRDTO;
import br.org.gam.api.event.missa.application.MissaApiModels.ReopenDTO;
import br.org.gam.api.event.missa.application.MissaApiModels.ReplaceMissaDTO;
import br.org.gam.api.event.missa.application.MissaApiModels.ResponsibilityRDTO;
import br.org.gam.api.event.missa.domain.MissaResponsibility;
import br.org.gam.api.event.missa.persistence.MissaEntity;
import br.org.gam.api.event.missa.persistence.MissaRepository;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.event.persistence.EventRepository;
import br.org.gam.api.gamLocation.application.GamLocationEntityLoader;
import br.org.gam.api.gamLocation.persistence.GamLocationEntity;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.presence.persistence.PresenceEntity;
import br.org.gam.api.presence.persistence.PresenceRepository;
import br.org.gam.api.rbac.permission.application.PermissionEntityLoader;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.rbac.permission.persistence.PermissionEntity;
import br.org.gam.api.security.SecurityUtils;
import br.org.gam.api.shared.activitylog.ActivityAction;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.activitylog.ActivityTargetType;
import br.org.gam.api.shared.exception.ConflictException;
import br.org.gam.api.shared.exception.InvalidCommandException;
import br.org.gam.api.shared.exception.NotFoundException;
import br.org.gam.api.shared.exception.RequestValidationException;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import br.org.gam.api.shared.validation.RequiredReason;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.AuditorAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class MissaUseCaseSupport {
    final MissaRepository missaRepository;
    final EventRepository eventRepository;
    final EventMapper eventMapper;
    final EventSecurity eventSecurity;
    final GamLocationEntityLoader locationLoader;
    final PermissionEntityLoader permissionLoader;
    final EntityManager entityManager;
    final PresenceRepository presenceRepository;
    final SecurityUtils securityUtils;
    final ActivityEvents activityEvents;
    final JdbcTemplate jdbcTemplate;
    final AuditorAware<UUID> auditorAware;
    final Clock clock;

    MissaUseCaseSupport(
            MissaRepository missaRepository,
            EventRepository eventRepository,
            EventMapper eventMapper,
            EventSecurity eventSecurity,
            GamLocationEntityLoader locationLoader,
            PermissionEntityLoader permissionLoader,
            EntityManager entityManager,
            PresenceRepository presenceRepository,
            SecurityUtils securityUtils,
            ActivityEvents activityEvents,
            JdbcTemplate jdbcTemplate,
            AuditorAware<UUID> auditorAware,
            Clock clock
    ) {
        this.missaRepository = missaRepository;
        this.eventRepository = eventRepository;
        this.eventMapper = eventMapper;
        this.eventSecurity = eventSecurity;
        this.locationLoader = locationLoader;
        this.permissionLoader = permissionLoader;
        this.entityManager = entityManager;
        this.presenceRepository = presenceRepository;
        this.securityUtils = securityUtils;
        this.activityEvents = activityEvents;
        this.jdbcTemplate = jdbcTemplate;
        this.auditorAware = auditorAware;
        this.clock = clock;
    }

    MutationContext mutationContext(UUID id, Instant evaluationInstant) {
        EventEntity event = eventRepository.findActiveByIdForUpdate(id)
                .orElseThrow(() -> NotFoundException.resource("Missa", id));
        if (!eventSecurity.canGetEvent(event)) {
            throw NotFoundException.resource("Missa", id);
        }
        MissaEntity missa = missaRepository.findActiveByIdForUpdate(id)
                .orElseThrow(() -> NotFoundException.resource("Missa", id));
        if (event.getType() != EventType.MISSA || !Objects.equals(missa.getEvent().getId(), id)) {
            throw NotFoundException.resource("Missa", id);
        }
        return new MutationContext(missa, event, effectiveStatus(event, evaluationInstant));
    }

    MissaEntity required(UUID id) {
        MissaEntity missa = missaRepository.findById(id)
                .orElseThrow(() -> NotFoundException.resource("Missa", id));
        if (missa.getEvent().getType() != EventType.MISSA || !Objects.equals(missa.getEvent().getId(), id)) {
            throw NotFoundException.resource("Missa", id);
        }
        return missa;
    }

    void assertAssignmentMutable(
            UUID id,
            MissaResponsibility responsibility,
            EventStatus status,
            Instant evaluationInstant
    ) {
        if (status == EventStatus.SCHEDULED || status == EventStatus.COMPLETED) {
            return;
        }
        throw conflict(
                "MISSA_ASSIGNMENT_NOT_ALLOWED",
                "Missa assignments cannot change in the current lifecycle state.",
                Map.of(
                        "missaId", id,
                        "responsibility", responsibility.name(),
                        "status", status.name(),
                        "evaluationInstant", evaluationInstant
                )
        );
    }

    UUID singletonMemberId(UUID id, MissaResponsibility responsibility) {
        List<UUID> members = jdbcTemplate.queryForList(
                "SELECT member_id FROM missa_assignments WHERE missa_id = ? AND responsibility = ?",
                UUID.class,
                id,
                responsibility.name()
        );
        return members.isEmpty() ? null : members.getFirst();
    }

    boolean assignmentExists(UUID id, MissaResponsibility responsibility, UUID memberId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM missa_assignments "
                        + "WHERE missa_id = ? AND responsibility = ? AND member_id = ?)",
                Boolean.class,
                id,
                responsibility.name(),
                memberId
        );
        return Boolean.TRUE.equals(exists);
    }

    MemberStatus memberStatusForUpdate(UUID memberId) {
        List<String> statuses = jdbcTemplate.queryForList(
                "SELECT status::text FROM members WHERE id = ? AND deleted_at IS NULL FOR UPDATE",
                String.class,
                memberId
        );
        if (statuses.isEmpty()) {
            throw NotFoundException.resource("Member", memberId);
        }
        return MemberStatus.valueOf(statuses.getFirst());
    }

    MissaRDTO detail(MissaEntity missa, Instant evaluationInstant) {
        EnumMap<MissaResponsibility, List<AssignedMemberRDTO>> grouped = new EnumMap<>(MissaResponsibility.class);
        for (MissaResponsibility responsibility : MissaResponsibility.values()) {
            grouped.put(responsibility, new ArrayList<>());
        }
        jdbcTemplate.query(
                "SELECT a.responsibility, m.id, m.first_name, m.surname, m.status::text "
                        + "FROM missa_assignments a JOIN members m ON m.id = a.member_id "
                        + "WHERE a.missa_id = ?",
                rs -> {
                    while (rs.next()) {
                        grouped.get(MissaResponsibility.valueOf(rs.getString("responsibility"))).add(
                                new AssignedMemberRDTO(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("first_name"),
                                        rs.getString("surname"),
                                        MemberStatus.valueOf(rs.getString("status"))
                                )
                        );
                    }
                    return null;
                },
                missa.getId()
        );
        Comparator<AssignedMemberRDTO> memberOrder = Comparator
                .comparing(AssignedMemberRDTO::firstName)
                .thenComparing(AssignedMemberRDTO::surname)
                .thenComparing(AssignedMemberRDTO::id);
        List<ResponsibilityRDTO> assignments = new ArrayList<>();
        for (MissaResponsibility responsibility : MissaResponsibility.values()) {
            List<AssignedMemberRDTO> members = grouped.get(responsibility);
            members.sort(memberOrder);
            assignments.add(new ResponsibilityRDTO(responsibility, List.copyOf(members)));
        }
        return new MissaRDTO(
                missa.getId(),
                eventMapper.entityToRDTO(missa.getEvent(), evaluationInstant),
                List.copyOf(assignments)
        );
    }

    PermissionEntity resolveAudiencePermission(UUID permissionId) {
        if (permissionId == null) return null;
        PermissionEntity permission = permissionLoader.requiredById(permissionId);
        PermissionEnum current = PermissionEnum.fromCode(permission.getCode())
                .filter(candidate -> permission.isSystemManaged()
                        && candidate.getLabel().equals(permission.getLabel())
                        && candidate.getDescription().equals(permission.getDescription()))
                .orElseThrow(() -> NotFoundException.resource("Permission", permissionId));
        if (current != PermissionEnum.EVENT_GET_MEMBER && current != PermissionEnum.EVENT_GET_COORD) {
            throw InvalidCommandException.reason(
                    "EVENT_AUDIENCE_PERMISSION_INVALID",
                    "The selected permission is not a valid Event audience permission."
            );
        }
        if (!securityUtils.getLoggedUserAuthorities().contains(permission.getCode())) {
            throw new AccessDeniedException("The selected Event audience permission is required.");
        }
        return permission;
    }

    void validateDates(Instant beginDate, Instant endDate) {
        try {
            Event.validateDates(beginDate, endDate);
        } catch (IllegalArgumentException exception) {
            throw new RequestValidationException("body", "$", "RELATION");
        }
    }

    String normalizeTitle(String title) {
        try {
            return Event.normalizeTitle(title);
        } catch (IllegalArgumentException exception) {
            throw new RequestValidationException("body", "/title", "SIZE");
        }
    }

    String normalizeDescription(String description) {
        try {
            return Event.normalizeDescription(description);
        } catch (IllegalArgumentException exception) {
            throw new RequestValidationException("body", "/description", "SIZE");
        }
    }

    String normalizeOptionalReason(String rawReason, String message) {
        return rawReason == null ? null : RequiredReason.normalize(rawReason, message);
    }

    EventStatus effectiveStatus(EventEntity event, Instant evaluationInstant) {
        return Event.effectiveStatus(event.getStatus(), event.getEndDate(), evaluationInstant);
    }

    ConflictException transitionConflict(UUID id, EventStatus current, EventStatus requested) {
        return conflict(
                "EVENT_STATUS_TRANSITION_NOT_ALLOWED",
                "The requested Missa status transition is not allowed.",
                Map.of(
                        "eventId", id,
                        "currentStatus", current.name(),
                        "requestedStatus", requested.name()
                )
        );
    }

    ConflictException conflict(String code, String message, Map<String, Object> details) {
        return ConflictException.reason(code, message, details);
    }

    void activity(
            ActivityAction action,
            UUID id,
            String reason,
            String summary,
            Map<String, Object> metadata
    ) {
        activityEvents.moduleActivity(action, ActivityTargetType.MISSA, id, reason, summary, metadata);
    }

    record MutationContext(MissaEntity missa, EventEntity event, EventStatus status) {
    }
}
