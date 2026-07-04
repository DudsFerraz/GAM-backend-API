package br.org.gam.api.presence.application.useCases;

import br.org.gam.api.event.application.EventSecurity;
import br.org.gam.api.event.application.EventEntityLoader;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.presence.application.PresenceMapper;
import br.org.gam.api.presence.application.PresenceRDTO;
import br.org.gam.api.presence.persistence.PresenceEntity;
import br.org.gam.api.presence.persistence.PresenceRepository;
import br.org.gam.api.presence.persistence.PresenceSpecifications;
import br.org.gam.api.shared.exception.NotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class GetPresence {
    private final PresenceMapper presenceMapper;
    private final PresenceRepository presenceRepo;
    private final EventSecurity eventSecurity;
    private final EventEntityLoader getEventInstance;

    public GetPresence(PresenceMapper presenceMapper, PresenceRepository presenceRepo, EventSecurity eventSecurity, EventEntityLoader getEventInstance) {
        this.presenceMapper = presenceMapper;
        this.presenceRepo = presenceRepo;
        this.eventSecurity = eventSecurity;
        this.getEventInstance = getEventInstance;
    }
    public PresenceRDTO byIds(UUID memberId, UUID eventId) {
        Specification<PresenceEntity> spec = PresenceSpecifications.fetchEvent()
                .and(PresenceSpecifications.fetchMember())
                .and(PresenceSpecifications.filterByEventId(eventId))
                .and(PresenceSpecifications.filterByMemberId(memberId));

        return presenceRepo.findOne(spec)
                .map(presenceMapper::entityToRDTO)
                .orElseThrow(() -> NotFoundException.resource("Presence", "%s:%s".formatted(memberId, eventId)));
    }
    public Page<PresenceRDTO> allByEvent(UUID eventId, Pageable pageable) {
        EventEntity eventEntity = getEventInstance.requiredById(eventId);
        if(!eventSecurity.canGetEvent(eventEntity)) throw NotFoundException.resource("Event", eventId);

        Specification<PresenceEntity> spec = PresenceSpecifications.fetchEvent()
                .and(PresenceSpecifications.fetchMember())
                .and(PresenceSpecifications.filterByEventId(eventId));

        Page<PresenceEntity> entitiesPage = presenceRepo.findAll(spec, pageable);
        return entitiesPage.map(presenceMapper::entityToRDTO);
    }
    public Page<PresenceRDTO> allByMember(UUID memberId, Pageable pageable) {

        Specification<PresenceEntity> spec = PresenceSpecifications.fetchEvent()
                .and(PresenceSpecifications.fetchMember())
                .and(PresenceSpecifications.filterByMemberId(memberId));

        Page<PresenceEntity> entitiesPage = presenceRepo.findAll(spec, pageable);
        return entitiesPage.map(presenceMapper::entityToRDTO);
    }
}
