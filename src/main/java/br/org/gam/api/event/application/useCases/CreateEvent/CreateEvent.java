package br.org.gam.api.event.application.useCases.CreateEvent;

import br.org.gam.api.event.application.EventMapper;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.event.persistence.EventRepository;
import br.org.gam.api.location.application.useCases.GetLocationInstance.GetLocationInstance;
import br.org.gam.api.location.persistence.LocationEntity;
import br.org.gam.api.rbac.Permission.application.useCases.GetPermissionInstance.GetPermissionInstance;
import br.org.gam.api.rbac.Permission.persistence.PermissionEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class CreateEvent {

    private final EventRepository eventRepository;
    private final GetLocationInstance getLocationInstanceService;
    private final EventMapper eventMapper;
    private final GetPermissionInstance getPermissionInstance;
    public CreateEvent(EventRepository eventRepository, GetLocationInstance getLocationInstanceService, EventMapper eventMapper, GetPermissionInstance getPermissionInstance) {
        this.eventRepository = eventRepository;
        this.getLocationInstanceService = getLocationInstanceService;
        this.eventMapper = eventMapper;
        this.getPermissionInstance = getPermissionInstance;
    }

    @Transactional
    public CreateEventRDTO create(CreateEventDTO dto) {

        LocationEntity eventLocation = getLocationInstanceService.entityById(dto.locationId());
        PermissionEntity requiredPermission = getPermissionInstance.entityById(dto.requiredPermissionId());

        Event newEvent = Event.register(dto.title(), dto.description(), dto.beginDate(), dto.endDate(), dto.type());

        EventEntity newEventEntity = eventMapper.domainToEntity(newEvent);
        newEventEntity.setLocation(eventLocation);
        newEventEntity.setRequiredPermission(requiredPermission);

        EventEntity savedEventEntity = eventRepository.save(newEventEntity);

        return eventMapper.entityToCreateEventRDTO(savedEventEntity);
    }

}
