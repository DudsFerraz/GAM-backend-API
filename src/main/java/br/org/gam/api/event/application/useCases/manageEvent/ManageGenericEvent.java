package br.org.gam.api.event.application.useCases.manageEvent;

import br.org.gam.api.event.application.EventEntityLoader;
import br.org.gam.api.event.application.EventMapper;
import br.org.gam.api.event.application.EventRDTO;
import br.org.gam.api.event.application.EventSecurity;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.event.persistence.EventRepository;
import br.org.gam.api.gamLocation.application.GamLocationEntityLoader;
import br.org.gam.api.gamLocation.persistence.GamLocationEntity;
import br.org.gam.api.presence.persistence.PresenceRepository;
import br.org.gam.api.rbac.permission.application.PermissionEntityLoader;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.rbac.permission.persistence.PermissionEntity;
import br.org.gam.api.security.SecurityUtils;
import br.org.gam.api.shared.activitylog.ActivityAction;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.exception.ConflictException;
import br.org.gam.api.shared.exception.InvalidCommandException;
import br.org.gam.api.shared.exception.NotFoundException;
import br.org.gam.api.shared.validation.RequiredReason;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManageGenericEvent {
    private final EventEntityLoader eventLoader;
    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final EventSecurity eventSecurity;
    private final GamLocationEntityLoader locationLoader;
    private final PermissionEntityLoader permissionLoader;
    private final PresenceRepository presenceRepository;
    private final SecurityUtils securityUtils;
    private final ActivityEvents activityEvents;

    public ManageGenericEvent(EventEntityLoader eventLoader, EventRepository eventRepository, EventMapper eventMapper,
                              EventSecurity eventSecurity, GamLocationEntityLoader locationLoader,
                              PermissionEntityLoader permissionLoader, PresenceRepository presenceRepository,
                              SecurityUtils securityUtils, ActivityEvents activityEvents) {
        this.eventLoader = eventLoader;
        this.eventRepository = eventRepository;
        this.eventMapper = eventMapper;
        this.eventSecurity = eventSecurity;
        this.locationLoader = locationLoader;
        this.permissionLoader = permissionLoader;
        this.presenceRepository = presenceRepository;
        this.securityUtils = securityUtils;
        this.activityEvents = activityEvents;
    }

    @Transactional
    public EventRDTO replace(UUID eventId, EventReplacementDTO dto) {
        Instant evaluationInstant = Instant.now();
        Event.validateDates(dto.beginDate(), dto.endDate());
        String title = Event.normalizeTitle(dto.title());
        String description = Event.normalizeDescription(dto.description());
        String reason = dto.reason() == null ? null
                : RequiredReason.normalize(dto.reason(), "An Event update reason must contain 1 to 2000 characters.");

        EventEntity entity = manageableForUpdate(eventId);
        EventStatus fromStatus = effectiveStatus(entity, evaluationInstant);
        if (fromStatus == EventStatus.FINALIZED || fromStatus == EventStatus.CANCELLED) {
            throw transitionConflict(eventId, fromStatus, fromStatus);
        }
        if (fromStatus == EventStatus.LOCKED && dto.endDate().isAfter(evaluationInstant)) {
            throw transitionConflict(eventId, fromStatus, EventStatus.SCHEDULED);
        }

        GamLocationEntity location = locationLoader.requiredByIdForUpdate(dto.gamLocationId());
        PermissionEntity permission = resolveAudiencePermission(dto.requiredPermissionId());
        UUID currentPermissionId = entity.getRequiredPermission() == null ? null : entity.getRequiredPermission().getId();
        boolean audienceChanged = !Objects.equals(currentPermissionId, dto.requiredPermissionId());
        if (audienceChanged && reason == null) {
            throw InvalidCommandException.reason("Changing an Event audience requires an audit reason.");
        }

        List<String> changedFields = new ArrayList<>();
        if (!Objects.equals(entity.getTitle(), title)) changedFields.add("title");
        if (!Objects.equals(entity.getDescription(), description)) changedFields.add("description");
        if (!Objects.equals(entity.getLocation().getId(), dto.gamLocationId())) changedFields.add("gamLocationId");
        if (audienceChanged) changedFields.add("requiredPermissionId");
        if (!Objects.equals(entity.getBeginDate(), dto.beginDate())) changedFields.add("beginDate");
        if (!Objects.equals(entity.getEndDate(), dto.endDate())) changedFields.add("endDate");

        if (changedFields.isEmpty()) {
            return eventMapper.entityToRDTO(entity, evaluationInstant);
        }

        entity.setTitle(title);
        entity.setDescription(description);
        entity.setLocation(location);
        entity.setRequiredPermission(permission);
        entity.setBeginDate(dto.beginDate());
        entity.setEndDate(dto.endDate());
        if (fromStatus == EventStatus.SCHEDULED || fromStatus == EventStatus.COMPLETED) {
            entity.setStatus(Event.effectiveStatus(EventStatus.SCHEDULED, dto.endDate(), evaluationInstant));
        }
        eventRepository.saveAndFlush(entity);

        EventStatus toStatus = effectiveStatus(entity, evaluationInstant);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("changedFields", List.copyOf(changedFields));
        if (fromStatus != toStatus) {
            metadata.put("fromStatus", fromStatus.name());
            metadata.put("toStatus", toStatus.name());
        }
        activityEvents.eventChanged(
                ActivityAction.EVENT_UPDATED, eventId, reason, "Event updated", metadata
        );
        return eventMapper.entityToRDTO(entity, evaluationInstant);
    }

    @Transactional
    public EventRDTO lock(UUID eventId) {
        return transition(eventId, EventStatus.LOCKED, null, EventStatus.COMPLETED);
    }

    @Transactional
    public EventRDTO finalizeEvent(UUID eventId) {
        return transition(eventId, EventStatus.FINALIZED, null, EventStatus.COMPLETED, EventStatus.LOCKED);
    }

    @Transactional
    public EventRDTO cancel(UUID eventId, EventReasonDTO dto) {
        String reason = RequiredReason.normalize(dto.reason(), "Event cancellation requires an audit reason.");
        return transition(eventId, EventStatus.CANCELLED, reason, EventStatus.SCHEDULED);
    }

    @Transactional
    public EventRDTO reopen(UUID eventId, ReopenEventDTO dto) {
        String reason = RequiredReason.normalize(dto.reason(), "Event reopening requires an audit reason.");
        if (dto.targetStatus() != EventStatus.LOCKED && dto.targetStatus() != EventStatus.COMPLETED) {
            throw InvalidCommandException.reason("Reopening targetStatus must be LOCKED or COMPLETED.");
        }
        return dto.targetStatus() == EventStatus.LOCKED
                ? transition(eventId, EventStatus.LOCKED, reason, EventStatus.FINALIZED)
                : transition(eventId, EventStatus.COMPLETED, reason, EventStatus.LOCKED, EventStatus.FINALIZED);
    }

    @Transactional
    public void delete(UUID eventId, EventReasonDTO dto) {
        String reason = RequiredReason.normalize(dto.reason(), "Event deletion requires an audit reason.");
        Instant evaluationInstant = Instant.now();
        EventEntity entity = manageableForUpdate(eventId);
        EventStatus status = effectiveStatus(entity, evaluationInstant);
        if (status == EventStatus.LOCKED || status == EventStatus.FINALIZED) {
            throw transitionConflict(eventId, status, status);
        }

        long activePresenceCount = presenceRepository.countByEvent_Id(eventId);
        if (activePresenceCount > 0) {
            throw ConflictException.resource(
                    "EVENT_HAS_PRESENCES", "Event", eventId,
                    "The Event has active Presence records.",
                    Map.of("eventId", eventId, "activePresenceCount", activePresenceCount)
            );
        }

        eventRepository.delete(entity);
        activityEvents.eventChanged(
                ActivityAction.EVENT_DELETED, eventId, reason, "Event deleted",
                Map.of(
                        "type", entity.getType().name(),
                        "fromStatus", status.name(),
                        "gamLocationId", entity.getLocation().getId()
                )
        );
    }

    private EventRDTO transition(UUID eventId, EventStatus targetStatus, String reason,
                                 EventStatus... allowedSources) {
        Instant evaluationInstant = Instant.now();
        EventEntity entity = manageableForUpdate(eventId);
        EventStatus currentStatus = effectiveStatus(entity, evaluationInstant);
        if (!transitionAllowed(currentStatus, allowedSources)) {
            throw transitionConflict(eventId, currentStatus, targetStatus);
        }

        int updated = updateStatus(eventId, entity.getStatus(), targetStatus, reason);
        if (updated == 0) {
            entity = manageableForUpdate(eventId);
            currentStatus = effectiveStatus(entity, evaluationInstant);
            if (!transitionAllowed(currentStatus, allowedSources)) {
                throw transitionConflict(eventId, currentStatus, targetStatus);
            }
            updated = updateStatus(eventId, entity.getStatus(), targetStatus, reason);
            if (updated == 0) {
                throw ConflictException.resource(
                        "RESOURCE_CONFLICT", "Event", eventId,
                        "The Event changed concurrently; retry the command."
                );
            }
        }

        activityEvents.eventChanged(
                actionFor(targetStatus, currentStatus), eventId, reason, "Event status changed",
                Map.of("fromStatus", currentStatus.name(), "toStatus", targetStatus.name())
        );
        EventEntity updatedEntity = eventLoader.requiredById(eventId);
        return eventMapper.entityToRDTO(updatedEntity, evaluationInstant);
    }

    private int updateStatus(UUID eventId, EventStatus expectedStatus, EventStatus targetStatus, String reason) {
        return eventRepository.updateStatusIfCurrent(
                eventId,
                expectedStatus.name(),
                targetStatus.name(),
                targetStatus == EventStatus.CANCELLED ? reason : null
        );
    }

    private EventEntity manageableForUpdate(UUID eventId) {
        EventEntity entity = eventLoader.requiredByIdForUpdate(eventId);
        if (!eventSecurity.canGetEvent(entity)) {
            throw NotFoundException.resource("Event", eventId);
        }
        if (entity.getType() != EventType.GENERIC) {
            throw ConflictException.resource(
                    "EVENT_TYPE_NOT_MANAGEABLE", "Event", eventId,
                    "Only Generic Events can be managed through this API."
            );
        }
        return entity;
    }

    private PermissionEntity resolveAudiencePermission(UUID permissionId) {
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

    private EventStatus effectiveStatus(EventEntity entity, Instant evaluationInstant) {
        return Event.effectiveStatus(entity.getStatus(), entity.getEndDate(), evaluationInstant);
    }

    private boolean transitionAllowed(EventStatus source, EventStatus... allowedSources) {
        for (EventStatus allowedSource : allowedSources) {
            if (source == allowedSource) return true;
        }
        return false;
    }

    private ActivityAction actionFor(EventStatus target, EventStatus source) {
        if (target == EventStatus.CANCELLED) return ActivityAction.EVENT_CANCELLED;
        if (target == EventStatus.FINALIZED) return ActivityAction.EVENT_FINALIZED;
        if (target == EventStatus.LOCKED && source != EventStatus.FINALIZED) return ActivityAction.EVENT_LOCKED;
        return ActivityAction.EVENT_REOPENED;
    }

    private ConflictException transitionConflict(UUID eventId, EventStatus current, EventStatus requested) {
        return ConflictException.resource(
                "EVENT_STATUS_TRANSITION_NOT_ALLOWED", "Event", eventId,
                "The requested Event status transition is not allowed.",
                Map.of(
                        "eventId", eventId,
                        "currentStatus", current.name(),
                        "requestedStatus", requested.name()
                )
        );
    }
}
