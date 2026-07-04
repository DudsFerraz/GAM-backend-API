package br.org.gam.api.presence.application.useCases.RegisterPresence;

import br.org.gam.api.event.application.EventEntityLoader;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.member.application.MemberEntityLoader;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.presence.application.PresenceMapper;
import br.org.gam.api.presence.persistence.PresenceEntity;
import br.org.gam.api.presence.persistence.PresenceRepository;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.exception.ConflictException;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterPresence {
    private final PresenceRepository presenceRepo;
    private final PresenceMapper presenceMapper;
    private final MemberEntityLoader getMemberInstance;
    private final EventEntityLoader getEventInstance;
    private final ActivityEvents activityEvents;

    public RegisterPresence(PresenceRepository presenceRepo, PresenceMapper presenceMapper,
                            MemberEntityLoader getMemberInstance, EventEntityLoader getEventInstance,
                            ActivityEvents activityEvents) {
        this.presenceRepo = presenceRepo;
        this.presenceMapper = presenceMapper;
        this.getMemberInstance = getMemberInstance;
        this.getEventInstance = getEventInstance;
        this.activityEvents = activityEvents;
    }

    @Transactional
    public RegisterPresenceRDTO register(RegisterPresenceDTO dto) {
        if(presenceRepo.existsByMember_IdAndEvent_Id(dto.memberId(), dto.eventId())){
            throw ConflictException.resource(
                    "Presence",
                    "%s:%s".formatted(dto.memberId(), dto.eventId()),
                    "Presence already registered"
            );
        }

        MemberEntity presentMember = getMemberInstance.requiredById(dto.memberId());
        EventEntity relatedEvent = getEventInstance.requiredById(dto.eventId());

        Objects.requireNonNull(presentMember, "Present member must not be null");
        Objects.requireNonNull(relatedEvent, "Presence event must not be null");

        PresenceEntity newPresenceEntity = new PresenceEntity();
        newPresenceEntity.setId(UUIDGenerator.generateUUIDV7());
        newPresenceEntity.setMember(presentMember);
        newPresenceEntity.setEvent(relatedEvent);
        newPresenceEntity.setObservations(dto.observations() == null ? "" : dto.observations().trim());

        PresenceEntity savedPresenceEntity = presenceRepo.save(newPresenceEntity);

        activityEvents.presenceRegistered(
                newPresenceEntity.getId(),
                dto.memberId(),
                dto.eventId()
        );

        return presenceMapper.entityToRegisterPresenceRDTO(savedPresenceEntity);
    }
}
