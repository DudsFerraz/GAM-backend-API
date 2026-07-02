package br.org.gam.api.event.application;

import br.org.gam.api.event.application.useCases.CreateEvent.CreateEventRDTO;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.location.application.LocationMapper;
import br.org.gam.api.rbac.Permission.application.PermissionMapper;
import br.org.gam.api.shared.auditing.IgnoreFullAuditFields;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {LocationMapper.class, PermissionMapper.class})
public interface EventMapper {
    @IgnoreFullAuditFields
    EventEntity domainToEntity(Event event);
    Event entityToDomain(EventEntity eventEntity);
    CreateEventRDTO entityToCreateEventRDTO(EventEntity eventEntity);
    EventRDTO entityToEventRDTO(EventEntity eventEntity);
}
