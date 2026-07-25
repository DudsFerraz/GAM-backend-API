package br.org.gam.api.presence.application;

import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.presence.application.useCases.registerPresence.RegisterPresenceRDTO;
import br.org.gam.api.presence.persistence.PresenceEntity;
import java.time.Instant;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PresenceMapper {

    // =====================================================================================
    // Persistence -> RDTO
    // =====================================================================================

    default RegisterPresenceRDTO entityToRegisterPresenceRDTO(PresenceEntity presenceEntity) {
        var member = presenceEntity.getMember();
        var event = presenceEntity.getEvent();
        return new RegisterPresenceRDTO(
                presenceEntity.getId(),
                new PresenceMemberRDTO(
                        member.getId(),
                        member.getName().firstName(),
                        member.getName().surname(),
                        member.getStatus()
                ),
                new PresenceEventRDTO(
                        event.getId(),
                        event.getTitle(),
                        event.getBeginDate(),
                        event.getEndDate(),
                        event.getType(),
                        event.getStatus()
                ),
                presenceEntity.getObservations(),
                presenceEntity.getCreatedAt()
        );
    }

    default PresenceRDTO entityToRDTO(PresenceEntity presenceEntity) {
        return entityToRDTO(presenceEntity, Instant.now());
    }

    default PresenceRDTO entityToRDTO(PresenceEntity presenceEntity, Instant evaluationInstant) {
        var member = presenceEntity.getMember();
        var event = presenceEntity.getEvent();
        EventStatus effectiveStatus = Event.effectiveStatus(
                event.getStatus(), event.getEndDate(), evaluationInstant
        );
        return new PresenceRDTO(
                presenceEntity.getId(),
                new PresenceMemberRDTO(
                        member.getId(),
                        member.getName().firstName(),
                        member.getName().surname(),
                        member.getStatus()
                ),
                new PresenceEventRDTO(
                        event.getId(),
                        event.getTitle(),
                        event.getBeginDate(),
                        event.getEndDate(),
                        event.getType(),
                        effectiveStatus
                ),
                presenceEntity.getObservations(),
                presenceEntity.getCreatedAt()
        );
    }
}
