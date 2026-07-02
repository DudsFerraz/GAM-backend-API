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

    // =====================================================================================
    // Domain <-> Persistence
    // =====================================================================================

    @IgnoreFullAuditFields
    EventEntity domainToEntity(Event event);

    Event entityToDomain(EventEntity eventEntity);

    // =====================================================================================
    // Persistence -> RDTO
    // =====================================================================================

    CreateEventRDTO entityToCreateEventRDTO(EventEntity eventEntity);

    EventRDTO entityToRDTO(EventEntity eventEntity);
}
