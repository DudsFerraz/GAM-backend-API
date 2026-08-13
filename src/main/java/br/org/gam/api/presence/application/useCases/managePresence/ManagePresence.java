package br.org.gam.api.presence.application.useCases.managePresence;

import br.org.gam.api.event.application.EventEntityLoader;
import br.org.gam.api.event.application.EventSecurity;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.presence.application.PresenceMapper;
import br.org.gam.api.presence.application.PresenceRDTO;
import br.org.gam.api.presence.persistence.PresenceEntity;
import br.org.gam.api.presence.persistence.PresenceRepository;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.exception.ConflictException;
import br.org.gam.api.shared.exception.InvalidCommandException;
import br.org.gam.api.shared.exception.NotFoundException;
import br.org.gam.api.shared.validation.RequiredReason;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManagePresence {
    private final EventEntityLoader eventLoader;
    private final EventSecurity eventSecurity;
    private final PresenceRepository presenceRepository;
    private final PresenceMapper presenceMapper;
    private final ActivityEvents activityEvents;
    private final JdbcTemplate jdbcTemplate;

    public ManagePresence(
            EventEntityLoader eventLoader,
            EventSecurity eventSecurity,
            PresenceRepository presenceRepository,
            PresenceMapper presenceMapper,
            ActivityEvents activityEvents,
            JdbcTemplate jdbcTemplate
    ) {
        this.eventLoader = eventLoader;
        this.eventSecurity = eventSecurity;
        this.presenceRepository = presenceRepository;
        this.presenceMapper = presenceMapper;
        this.activityEvents = activityEvents;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public PresenceRDTO updateObservations(
            UUID eventId,
            UUID memberId,
            UpdatePresenceObservationsDTO dto
    ) {
        MutationContext context = loadMutationContext(eventId, memberId, "PRESENCE_EDIT_NOT_ALLOWED");
        String normalized = normalizeObservations(dto.observations());
        String previous = context.presence().getObservations();
        if (Objects.equals(previous, normalized)) {
            return presenceMapper.entityToRDTO(context.presence(), context.evaluationInstant());
        }

        context.presence().setObservations(normalized);
        PresenceEntity saved = presenceRepository.save(context.presence());
        activityEvents.presenceUpdated(
                saved.getId(),
                memberId,
                eventId,
                previous,
                normalized
        );
        return presenceMapper.entityToRDTO(saved, context.evaluationInstant());
    }

    @Transactional
    public void remove(UUID eventId, UUID memberId, RemovePresenceDTO dto) {
        String reason = RequiredReason.normalize(
                dto.reason(),
                "Presence removal requires an audit reason containing 1 to 2000 characters."
        );
        MutationContext context = loadMutationContext(
                eventId,
                memberId,
                "PRESENCE_REMOVAL_NOT_ALLOWED"
        );

        if (context.event().getType() == EventType.MISSA
                && (context.status() == EventStatus.SCHEDULED || context.status() == EventStatus.COMPLETED)
                && missaAssignmentExists(eventId, memberId)) {
            throw ConflictException.reason(
                    "MISSA_ASSIGNMENT_REQUIRES_PRESENCE",
                    "The Member's Missa assignment requires this active Presence.",
                    Map.of("missaId", eventId, "memberId", memberId)
            );
        }

        PresenceEntity presence = context.presence();
        presenceRepository.delete(presence);
        activityEvents.presenceRemoved(
                presence.getId(),
                memberId,
                eventId,
                presence.getObservations(),
                reason
        );
    }

    private MutationContext loadMutationContext(
            UUID eventId,
            UUID memberId,
            String conflictCode
    ) {
        EventEntity event = eventLoader.requiredByIdForUpdate(eventId);
        if (!eventSecurity.canGetEvent(event)) {
            throw NotFoundException.resource("Event", eventId);
        }

        Instant evaluationInstant = Instant.now();
        PresenceEntity presence = presenceRepository.findByMember_IdAndEvent_Id(memberId, eventId)
                .orElseThrow(() -> NotFoundException.resource(
                        "Presence", "%s:%s".formatted(eventId, memberId)
                ));
        EventStatus status = Event.effectiveStatus(
                event.getStatus(), event.getEndDate(), evaluationInstant
        );
        if (status == EventStatus.LOCKED || status == EventStatus.FINALIZED) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("eventId", eventId);
            details.put("presenceId", presence.getId());
            details.put("status", status.name());
            throw ConflictException.resource(
                    conflictCode,
                    "Presence",
                    presence.getId(),
                    "Presence mutation is not allowed for the Event in its current state.",
                    details
            );
        }

        return new MutationContext(presence, event, status, evaluationInstant);
    }

    private boolean missaAssignmentExists(UUID missaId, UUID memberId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM missa_assignments WHERE missa_id = ? AND member_id = ?)",
                Boolean.class,
                missaId,
                memberId
        );
        return Boolean.TRUE.equals(exists);
    }

    private String normalizeObservations(String observations) {
        if (observations == null) {
            return null;
        }
        String normalized = observations.strip();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.codePointCount(0, normalized.length()) > 2_000) {
            throw InvalidCommandException.reason(
                    "Presence observations must contain at most 2000 characters."
            );
        }
        return normalized;
    }

    private record MutationContext(
            PresenceEntity presence,
            EventEntity event,
            EventStatus status,
            Instant evaluationInstant
    ) {
    }
}
