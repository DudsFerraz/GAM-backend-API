package br.org.gam.api.event.application;

import br.org.gam.api.event.application.useCases.createEvent.CreateEventRDTO;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.rbac.permission.application.PermissionMapper;
import br.org.gam.api.shared.auditing.IgnoreFullAuditFields;
import br.org.gam.api.gamLocation.application.GamLocationMapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {GamLocationMapper.class, PermissionMapper.class})
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

    @Mapping(source = "location", target = "gamLocation")
    EventRDTO entityToRDTO(EventEntity eventEntity);
}
