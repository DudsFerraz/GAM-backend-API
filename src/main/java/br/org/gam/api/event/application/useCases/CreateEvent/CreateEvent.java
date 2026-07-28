package br.org.gam.api.event.application.useCases.createEvent;

import br.org.gam.api.event.application.EventMapper;
import br.org.gam.api.event.application.EventRDTO;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.event.persistence.EventRepository;
import br.org.gam.api.gamLocation.application.GamLocationEntityLoader;
import br.org.gam.api.gamLocation.persistence.GamLocationEntity;
import br.org.gam.api.rbac.permission.application.PermissionEntityLoader;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.rbac.permission.persistence.PermissionEntity;
import br.org.gam.api.security.SecurityUtils;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.exception.InvalidCommandException;
import br.org.gam.api.shared.exception.NotFoundException;
import br.org.gam.api.shared.exception.RequestValidationException;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class CreateEvent {

    private final EventRepository eventRepository;
    private final GamLocationEntityLoader gamLocationEntityLoader;
    private final EventMapper eventMapper;
    private final PermissionEntityLoader getPermissionInstance;
    private final ActivityEvents activityEvents;
    private final SecurityUtils securityUtils;

    @Autowired
    public CreateEvent(EventRepository eventRepository, GamLocationEntityLoader gamLocationEntityLoader,
                       EventMapper eventMapper, PermissionEntityLoader getPermissionInstance,
                       ActivityEvents activityEvents, SecurityUtils securityUtils) {
        this.eventRepository = eventRepository;
        this.gamLocationEntityLoader = gamLocationEntityLoader;
        this.eventMapper = eventMapper;
        this.getPermissionInstance = getPermissionInstance;
        this.activityEvents = activityEvents;
        this.securityUtils = securityUtils;
    }

    public CreateEvent(EventRepository eventRepository, GamLocationEntityLoader gamLocationEntityLoader,
                       EventMapper eventMapper, PermissionEntityLoader getPermissionInstance,
                       ActivityEvents activityEvents) {
        this(eventRepository, gamLocationEntityLoader, eventMapper, getPermissionInstance,
                activityEvents, new SecurityUtils());
    }

    @Transactional
    public CreateEventRDTO create(CreateEventDTO dto) {
        return create(dto, true);
    }

    @Transactional
    public EventRDTO create(CreateGenericEventDTO dto) {
        validateDateRelationship(dto.beginDate(), dto.endDate());
        String title = validatedTitle(dto.title());
        String description = validatedDescription(dto.description());
        Instant evaluationInstant = Instant.now();
        GamLocationEntity eventLocation = gamLocationEntityLoader.requiredByIdForUpdate(dto.gamLocationId());
        PermissionEntity requiredPermission = resolveGenericAudiencePermission(dto.requiredPermissionId());

        Event newEvent = Event.register(
                title, description, dto.beginDate(), dto.endDate(), EventType.GENERIC, evaluationInstant
        );
        EventEntity newEventEntity = eventMapper.domainToEntity(newEvent);
        newEventEntity.setLocation(eventLocation);
        newEventEntity.setRequiredPermission(requiredPermission);
        EventEntity savedEventEntity = eventRepository.save(newEventEntity);

        activityEvents.eventCreated(
                newEvent.getId(), savedEventEntity.getTitle(), newEvent.getType(), newEvent.getStatus(),
                dto.gamLocationId(), dto.requiredPermissionId()
        );
        return eventMapper.entityToRDTO(savedEventEntity, evaluationInstant);
    }

    private PermissionEntity resolveGenericAudiencePermission(java.util.UUID permissionId) {
        if (permissionId == null) return null;

        PermissionEntity permission = getPermissionInstance.requiredById(permissionId);
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

    @Transactional
    public CreateEventRDTO create(CreateEventDTO dto, boolean audit) {
        validateDateRelationship(dto.beginDate(), dto.endDate());
        String title = validatedTitle(dto.title());
        String description = validatedDescription(dto.description());

        GamLocationEntity eventLocation = gamLocationEntityLoader.requiredByIdForUpdate(dto.gamLocationId());
        PermissionEntity requiredPermission = dto.requiredPermissionId() == null
                ? null
                : getPermissionInstance.requiredById(dto.requiredPermissionId());

        Event newEvent = Event.register(
                title, description, dto.beginDate(), dto.endDate(),
                dto.type() == null ? EventType.GENERIC : dto.type()
        );

        EventEntity newEventEntity = eventMapper.domainToEntity(newEvent);
        newEventEntity.setLocation(eventLocation);
        newEventEntity.setRequiredPermission(requiredPermission);

        EventEntity savedEventEntity = eventRepository.save(newEventEntity);

        if (audit) {
            activityEvents.eventCreated(
                    newEvent.getId(),
                    savedEventEntity.getTitle(),
                    newEvent.getType(),
                    newEvent.getStatus(),
                    dto.gamLocationId(),
                    dto.requiredPermissionId()
            );
        }

        return eventMapper.entityToCreateEventRDTO(savedEventEntity);
    }

    private void validateDateRelationship(Instant beginDate, Instant endDate) {
        if (!endDate.isAfter(beginDate)) {
            throw new RequestValidationException("body", "$", "RELATION");
        }
    }

    private String validatedTitle(String title) {
        try {
            return Event.normalizeTitle(title);
        } catch (IllegalArgumentException exception) {
            throw new RequestValidationException("body", "/title", "SIZE");
        }
    }

    private String validatedDescription(String description) {
        try {
            return Event.normalizeDescription(description);
        } catch (IllegalArgumentException exception) {
            throw new RequestValidationException("body", "/description", "SIZE");
        }
    }

}
