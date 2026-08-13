package br.org.gam.api.presence.application.useCases.registerPresence;

import br.org.gam.api.event.application.EventEntityLoader;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.event.application.EventSecurity;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.member.application.MemberEntityLoader;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.presence.application.PresenceEventRDTO;
import br.org.gam.api.presence.application.PresenceMemberRDTO;
import br.org.gam.api.presence.application.PresenceMapper;
import br.org.gam.api.presence.persistence.PresenceEntity;
import br.org.gam.api.presence.persistence.PresenceRepository;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.exception.ConflictException;
import br.org.gam.api.shared.exception.InvalidCommandException;
import br.org.gam.api.shared.exception.NotFoundException;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterPresence {
    private final PresenceRepository presenceRepo;
    private final PresenceMapper presenceMapper;
    private final MemberEntityLoader getMemberInstance;
    private final EventEntityLoader getEventInstance;
    private final ActivityEvents activityEvents;
    private final EventSecurity eventSecurity;
    private final PresenceConflictResolver conflictResolver;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public RegisterPresence(PresenceRepository presenceRepo, PresenceMapper presenceMapper,
                            MemberEntityLoader getMemberInstance, EventEntityLoader getEventInstance,
                            ActivityEvents activityEvents, EventSecurity eventSecurity,
                            PresenceConflictResolver conflictResolver,
                            EntityManager entityManager,
                            JdbcTemplate jdbcTemplate) {
        this.presenceRepo = presenceRepo;
        this.presenceMapper = presenceMapper;
        this.getMemberInstance = getMemberInstance;
        this.getEventInstance = getEventInstance;
        this.activityEvents = activityEvents;
        this.eventSecurity = eventSecurity;
        this.conflictResolver = conflictResolver;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    RegisterPresence(PresenceRepository presenceRepo, PresenceMapper presenceMapper,
                     MemberEntityLoader getMemberInstance, EventEntityLoader getEventInstance,
                     ActivityEvents activityEvents, EventSecurity eventSecurity) {
        this(
                presenceRepo,
                presenceMapper,
                getMemberInstance,
                getEventInstance,
                activityEvents,
                eventSecurity,
                null,
                null,
                null
        );
    }

    @Transactional
    public RegisterPresenceRDTO register(RegisterPresenceDTO dto) {
        EventEntity relatedEvent = getEventInstance.requiredByIdForUpdate(dto.eventId());
        if (!eventSecurity.canGetEvent(relatedEvent)) {
            throw NotFoundException.resource("Event", dto.eventId());
        }
        Instant evaluationInstant = Instant.now();
        EventStatus status = Event.effectiveStatus(
                relatedEvent.getStatus(), relatedEvent.getEndDate(), evaluationInstant
        );
        if (status != EventStatus.SCHEDULED && status != EventStatus.COMPLETED) {
            throw ConflictException.resource(
                    "PRESENCE_REGISTRATION_NOT_ALLOWED", "Event", dto.eventId(),
                    "Presence registration is not allowed for the Event in its current state.",
                    Map.of(
                            "eventId", dto.eventId(),
                            "status", status.name(),
                            "evaluationInstant", evaluationInstant
                    )
            );
        }

        if (presenceRepo.existsByMember_IdAndEvent_Id(dto.memberId(), dto.eventId())) {
            throw alreadyRegistered(dto, false);
        }

        MemberEntity presentMember = activeMemberReference(dto.memberId());

        Objects.requireNonNull(presentMember, "Present member must not be null");
        Objects.requireNonNull(relatedEvent, "Presence event must not be null");

        PresenceEntity newPresenceEntity = new PresenceEntity();
        newPresenceEntity.setId(UUIDGenerator.generateUUIDV7());
        newPresenceEntity.setMember(presentMember);
        newPresenceEntity.setEvent(relatedEvent);
        String observations = normalizeObservations(dto.observations());
        newPresenceEntity.setObservations(observations);

        PresenceEntity savedPresenceEntity;
        try {
            savedPresenceEntity = presenceRepo.save(newPresenceEntity);
            presenceRepo.flush();
        } catch (DataIntegrityViolationException exception) {
            if (!isPresenceUniquenessViolation(exception)) {
                throw exception;
            }
            throw alreadyRegistered(dto, true);
        }

        activityEvents.presenceRegistered(
                newPresenceEntity.getId(),
                dto.memberId(),
                dto.eventId(),
                observations
        );

        RegisterPresenceRDTO response = jdbcTemplate == null
                ? presenceMapper.entityToRegisterPresenceRDTO(savedPresenceEntity)
                : responseWithoutMemberAggregateLoad(savedPresenceEntity, status, evaluationInstant);
        return withEffectiveStatus(response, status);
    }

    private ConflictException alreadyRegistered(RegisterPresenceDTO dto, boolean afterConstraintViolation) {
        var existingPresenceId = afterConstraintViolation && conflictResolver != null
                ? conflictResolver.findWinningPresenceId(dto.memberId(), dto.eventId())
                : presenceRepo.findByMember_IdAndEvent_Id(dto.memberId(), dto.eventId())
                        .map(PresenceEntity::getId);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventId", dto.eventId());
        details.put("memberId", dto.memberId());
        existingPresenceId.ifPresent(id -> details.put("presenceId", id));
        return ConflictException.resource(
                "PRESENCE_ALREADY_REGISTERED",
                "Presence",
                "%s:%s".formatted(dto.eventId(), dto.memberId()),
                "Presence already registered",
                details
        );
    }

    private boolean isPresenceUniquenessViolation(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation
                    && "idx_presence_not_deleted".equals(constraintViolation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return exception.getMessage() != null
                && exception.getMessage().contains("idx_presence_not_deleted");
    }

    private RegisterPresenceRDTO withEffectiveStatus(RegisterPresenceRDTO response, EventStatus status) {
        if (response == null || response.event() == null) {
            return response;
        }
        PresenceEventRDTO event = response.event();
        return new RegisterPresenceRDTO(
                response.id(),
                response.member(),
                new PresenceEventRDTO(
                        event.id(),
                        event.title(),
                        event.beginDate(),
                        event.endDate(),
                        event.type(),
                        status
                ),
                response.observations(),
                response.registeredAt()
        );
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

    private MemberEntity activeMemberReference(java.util.UUID memberId) {
        if (entityManager == null || jdbcTemplate == null) {
            return getMemberInstance.requiredById(memberId);
        }
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM members WHERE id = ? AND deleted_at IS NULL)",
                Boolean.class,
                memberId
        );
        if (!Boolean.TRUE.equals(exists)) {
            throw NotFoundException.resource("Member", memberId);
        }
        return entityManager.getReference(MemberEntity.class, memberId);
    }

    private RegisterPresenceRDTO responseWithoutMemberAggregateLoad(
            PresenceEntity presence,
            EventStatus status,
            Instant evaluationInstant
    ) {
        Map<String, Object> member = jdbcTemplate.queryForMap(
                "SELECT first_name, surname, status::text AS status FROM members WHERE id = ?",
                presence.getMember().getId()
        );
        EventEntity event = presence.getEvent();
        return new RegisterPresenceRDTO(
                presence.getId(),
                new PresenceMemberRDTO(
                        presence.getMember().getId(),
                        member.get("first_name").toString(),
                        member.get("surname").toString(),
                        br.org.gam.api.member.domain.MemberStatus.valueOf(member.get("status").toString())
                ),
                new PresenceEventRDTO(
                        event.getId(),
                        event.getTitle(),
                        event.getBeginDate(),
                        event.getEndDate(),
                        event.getType(),
                        status
                ),
                presence.getObservations(),
                presence.getCreatedAt() == null ? evaluationInstant : presence.getCreatedAt()
        );
    }
}
