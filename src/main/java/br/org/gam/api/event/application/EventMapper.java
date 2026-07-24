package br.org.gam.api.event.application;

import br.org.gam.api.event.application.useCases.createEvent.CreateEventRDTO;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.rbac.permission.application.PermissionMapper;
import br.org.gam.api.shared.auditing.IgnoreFullAuditFields;
import br.org.gam.api.gamLocation.application.GamLocationMapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mapper;
import java.time.Instant;

@Mapper(componentModel = "spring", uses = {GamLocationMapper.class, PermissionMapper.class})
public interface EventMapper {

    // =====================================================================================
    // Domain <-> Persistence
    // =====================================================================================

    @IgnoreFullAuditFields
    @Mapping(target = "location", ignore = true)
    @Mapping(target = "requiredPermission", ignore = true)
    EventEntity domainToEntity(Event event);

    Event entityToDomain(EventEntity eventEntity);

    // =====================================================================================
    // Persistence -> RDTO
    // =====================================================================================

    CreateEventRDTO entityToCreateEventRDTO(EventEntity eventEntity);

    @Mapping(source = "location", target = "gamLocation")
    EventRDTO entityToRDTO(EventEntity eventEntity);

    default EventRDTO entityToRDTO(EventEntity eventEntity, Instant evaluationInstant) {
        EventRDTO mapped = entityToRDTO(eventEntity);
        EventStatus effectiveStatus = Event.effectiveStatus(
                eventEntity.getStatus(), eventEntity.getEndDate(), evaluationInstant
        );
        return new EventRDTO(
                mapped.id(), mapped.title(), mapped.description(), mapped.gamLocation(), mapped.requiredPermission(),
                mapped.beginDate(), mapped.endDate(), mapped.type(), effectiveStatus,
                effectiveStatus == EventStatus.CANCELLED ? mapped.cancellationReason() : null
        );
    }
}
