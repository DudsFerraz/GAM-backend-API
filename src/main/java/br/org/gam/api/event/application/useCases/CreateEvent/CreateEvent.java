package br.org.gam.api.event.application.useCases.CreateEvent;

import br.org.gam.api.event.application.EventMapper;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.event.persistence.EventRepository;
import br.org.gam.api.location.application.LocationEntityLoader;
import br.org.gam.api.location.persistence.LocationEntity;
import br.org.gam.api.rbac.Permission.application.PermissionEntityLoader;
import br.org.gam.api.rbac.Permission.persistence.PermissionEntity;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class CreateEvent {

    private final EventRepository eventRepository;
    private final LocationEntityLoader getLocationInstanceService;
    private final EventMapper eventMapper;
    private final PermissionEntityLoader getPermissionInstance;
    private final ActivityEvents activityEvents;

    public CreateEvent(EventRepository eventRepository, LocationEntityLoader getLocationInstanceService,
                       EventMapper eventMapper, PermissionEntityLoader getPermissionInstance,
                       ActivityEvents activityEvents) {
        this.eventRepository = eventRepository;
        this.getLocationInstanceService = getLocationInstanceService;
        this.eventMapper = eventMapper;
        this.getPermissionInstance = getPermissionInstance;
        this.activityEvents = activityEvents;
    }

    @Transactional
    public CreateEventRDTO create(CreateEventDTO dto) {
        return create(dto, true);
    }

    @Transactional
    public CreateEventRDTO create(CreateEventDTO dto, boolean audit) {

        LocationEntity eventLocation = getLocationInstanceService.requiredById(dto.locationId());
        PermissionEntity requiredPermission = getPermissionInstance.requiredById(dto.requiredPermissionId());

        Event newEvent = Event.register(dto.title(), dto.description(), dto.beginDate(), dto.endDate(), dto.type());

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
                    dto.locationId(),
                    dto.requiredPermissionId()
            );
        }

        return eventMapper.entityToCreateEventRDTO(savedEventEntity);
    }

}
