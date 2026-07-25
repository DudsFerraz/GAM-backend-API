package br.org.gam.api.presence.application.useCases;

import br.org.gam.api.event.application.EventSecurity;
import br.org.gam.api.event.application.EventEntityLoader;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.presence.application.PresenceMapper;
import br.org.gam.api.presence.application.PresenceRDTO;
import br.org.gam.api.presence.persistence.PresenceEntity;
import br.org.gam.api.presence.persistence.PresenceRepository;
import br.org.gam.api.presence.persistence.PresenceSpecifications;
import br.org.gam.api.presence.application.useCases.managePresence.ManagePresence;
import br.org.gam.api.presence.application.useCases.managePresence.RemovePresenceDTO;
import br.org.gam.api.presence.application.useCases.managePresence.UpdatePresenceObservationsDTO;
import br.org.gam.api.shared.exception.NotFoundException;
import br.org.gam.api.shared.exception.InvalidCommandException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class GetPresence {
    private final PresenceMapper presenceMapper;
    private final PresenceRepository presenceRepo;
    private final EventSecurity eventSecurity;
    private final EventEntityLoader getEventInstance;
    private final ManagePresence managePresence;

    public GetPresence(
            PresenceMapper presenceMapper,
            PresenceRepository presenceRepo,
            EventSecurity eventSecurity,
            EventEntityLoader getEventInstance,
            ManagePresence managePresence
    ) {
        this.presenceMapper = presenceMapper;
        this.presenceRepo = presenceRepo;
        this.eventSecurity = eventSecurity;
        this.getEventInstance = getEventInstance;
        this.managePresence = managePresence;
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

    public PresenceRDTO byEventAndMember(UUID eventId, UUID memberId) {
        EventEntity eventEntity = getEventInstance.requiredById(eventId);
        if (!eventSecurity.canGetEvent(eventEntity)) {
            throw NotFoundException.resource("Event", eventId);
        }

        Instant evaluationInstant = Instant.now();
        Specification<PresenceEntity> spec = PresenceSpecifications.fetchEvent()
                .and(PresenceSpecifications.fetchMember())
                .and(PresenceSpecifications.filterByEventId(eventId))
                .and(PresenceSpecifications.filterByMemberId(memberId));

        return presenceRepo.findOne(spec)
                .map(entity -> mapAtInstant(entity, evaluationInstant))
                .orElseThrow(() -> NotFoundException.resource(
                        "Presence", "%s:%s".formatted(eventId, memberId)
                ));
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

    public Page<PresenceRDTO> allByEvent(UUID eventId, String name, Pageable pageable) {
        EventEntity eventEntity = getEventInstance.requiredById(eventId);
        if (!eventSecurity.canGetEvent(eventEntity)) {
            throw NotFoundException.resource("Event", eventId);
        }

        String normalizedName = normalizeOptionalName(name);
        Specification<PresenceEntity> spec = PresenceSpecifications.fetchEvent()
                .and(PresenceSpecifications.fetchMember())
                .and(PresenceSpecifications.filterByEventId(eventId));
        if (normalizedName != null) {
            spec = spec.and(PresenceSpecifications.memberNameContains(normalizedName));
        }

        Instant evaluationInstant = Instant.now();
        Page<PresenceEntity> entitiesPage = presenceRepo.findAll(
                spec,
                eventRosterPageable(pageable)
        );
        return entitiesPage.map(entity -> mapAtInstant(entity, evaluationInstant));
    }
    public Page<PresenceRDTO> allByMember(UUID memberId, Pageable pageable) {

        Specification<PresenceEntity> spec = PresenceSpecifications.fetchEvent()
                .and(PresenceSpecifications.fetchMember())
                .and(PresenceSpecifications.filterByMemberId(memberId));

        Page<PresenceEntity> entitiesPage = presenceRepo.findAll(spec, pageable);
        return entitiesPage.map(presenceMapper::entityToRDTO);
    }

    public Page<PresenceRDTO> allByMemberOrdered(UUID memberId, Pageable pageable) {
        Instant evaluationInstant = Instant.now();
        Specification<PresenceEntity> spec = PresenceSpecifications.fetchEvent()
                .and(PresenceSpecifications.fetchMember())
                .and(PresenceSpecifications.filterByMemberId(memberId));

        Page<PresenceEntity> entitiesPage = presenceRepo.findAll(
                spec,
                memberHistoryPageable(pageable)
        );
        return entitiesPage.map(entity -> mapAtInstant(entity, evaluationInstant));
    }

    public PresenceRDTO updateObservations(
            UUID eventId,
            UUID memberId,
            UpdatePresenceObservationsDTO dto
    ) {
        return managePresence.updateObservations(eventId, memberId, dto);
    }

    public void remove(UUID eventId, UUID memberId, RemovePresenceDTO dto) {
        managePresence.remove(eventId, memberId, dto);
    }

    private PresenceRDTO mapAtInstant(PresenceEntity entity, Instant evaluationInstant) {
        PresenceRDTO mapped = presenceMapper.entityToRDTO(entity);
        if (mapped.event() == null) {
            return mapped;
        }
        var event = mapped.event();
        var effectiveEvent = new br.org.gam.api.presence.application.PresenceEventRDTO(
                event.id(),
                event.title(),
                event.beginDate(),
                event.endDate(),
                event.type(),
                br.org.gam.api.event.domain.Event.effectiveStatus(
                        entity.getEvent().getStatus(),
                        entity.getEvent().getEndDate(),
                        evaluationInstant
                )
        );
        return new PresenceRDTO(
                mapped.id(),
                mapped.member(),
                effectiveEvent,
                mapped.observations(),
                mapped.registeredAt()
        );
    }

    private Pageable eventRosterPageable(Pageable pageable) {
        Sort defaultSort = Sort.by(
                Sort.Order.asc("member.name.firstName"),
                Sort.Order.asc("member.name.surname"),
                Sort.Order.asc("id")
        );
        return withMappedSort(pageable, defaultSort, java.util.Map.of(
                "memberFirstName", "member.name.firstName",
                "memberSurname", "member.name.surname",
                "registeredAt", "createdAt"
        ));
    }

    private Pageable memberHistoryPageable(Pageable pageable) {
        Sort defaultSort = Sort.by(
                Sort.Order.desc("event.beginDate"),
                Sort.Order.desc("event.id"),
                Sort.Order.asc("id")
        );
        return withMappedSort(pageable, defaultSort, java.util.Map.of(
                "eventBeginDate", "event.beginDate",
                "eventTitle", "event.title",
                "registeredAt", "createdAt"
        ));
    }

    private Pageable withMappedSort(
            Pageable pageable,
            Sort defaultSort,
            java.util.Map<String, String> fields
    ) {
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
        }

        List<Sort.Order> orders = new ArrayList<>();
        pageable.getSort().forEach(order -> orders.add(
                new Sort.Order(order.getDirection(), fields.get(order.getProperty()))
        ));
        orders.add(Sort.Order.asc("id"));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
    }

    private String normalizeOptionalName(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.strip();
        if (normalized.isEmpty()) {
            throw InvalidCommandException.reason("Presence roster name filter must not be blank.");
        }
        return normalized;
    }
}
