package br.org.gam.api.presence.application.useCases.GetPresence;

import br.org.gam.api.event.application.EventNotFoundException;
import br.org.gam.api.event.application.EventSecurity;
import br.org.gam.api.event.application.useCases.GetEventInstance.GetEventInstance;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.presence.application.PresenceMapper;
import br.org.gam.api.presence.application.PresenceNotFoundException;
import br.org.gam.api.presence.application.PresenceRDTO;
import br.org.gam.api.presence.persistence.PresenceEntity;
import br.org.gam.api.presence.persistence.PresenceRepository;
import br.org.gam.api.presence.persistence.PresenceSpecifications;
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
    private final GetEventInstance getEventInstance;

    public GetPresence(PresenceMapper presenceMapper, PresenceRepository presenceRepo, EventSecurity eventSecurity, GetEventInstance getEventInstance) {
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
                .map(presenceMapper::entityToPresenceRDTO)
                .orElseThrow(() -> new PresenceNotFoundException(
                        String.format("member with id: %s has no presence registered in event with id: %s", memberId, eventId)
                        ));
    }
    public Page<PresenceRDTO> allByEvent(UUID eventId, Pageable pageable) {
        EventEntity eventEntity = getEventInstance.entityById(eventId);
        if(!eventSecurity.canGetEvent(eventEntity)) throw new EventNotFoundException("Could not find event with id " + eventId);

        Specification<PresenceEntity> spec = PresenceSpecifications.fetchEvent()
                .and(PresenceSpecifications.fetchMember())
                .and(PresenceSpecifications.filterByEventId(eventId));

        Page<PresenceEntity> entitiesPage = presenceRepo.findAll(spec, pageable);
        return entitiesPage.map(presenceMapper::entityToPresenceRDTO);
    }
    public Page<PresenceRDTO> allByMember(UUID memberId, Pageable pageable) {

        Specification<PresenceEntity> spec = PresenceSpecifications.fetchEvent()
                .and(PresenceSpecifications.fetchMember())
                .and(PresenceSpecifications.filterByMemberId(memberId));

        Page<PresenceEntity> entitiesPage = presenceRepo.findAll(spec, pageable);
        return entitiesPage.map(presenceMapper::entityToPresenceRDTO);
    }
}
