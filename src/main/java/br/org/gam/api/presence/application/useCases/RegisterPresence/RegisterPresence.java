package br.org.gam.api.presence.application.useCases.RegisterPresence;

import br.org.gam.api.event.application.EventEntityLoader;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.member.application.MemberEntityLoader;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.presence.application.PresenceConflictException;
import br.org.gam.api.presence.application.PresenceMapper;
import br.org.gam.api.presence.persistence.PresenceEntity;
import br.org.gam.api.presence.persistence.PresenceRepository;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class RegisterPresence {
    private final PresenceRepository presenceRepo;
    private final PresenceMapper presenceMapper;
    private final MemberEntityLoader getMemberInstance;
    private final EventEntityLoader getEventInstance;

    public RegisterPresence(PresenceRepository presenceRepo, PresenceMapper presenceMapper, MemberEntityLoader getMemberInstance, EventEntityLoader getEventInstance) {
        this.presenceRepo = presenceRepo;
        this.presenceMapper = presenceMapper;
        this.getMemberInstance = getMemberInstance;
        this.getEventInstance = getEventInstance;
    }
    public RegisterPresenceRDTO register(RegisterPresenceDTO dto) {
        if(presenceRepo.existsByMember_IdAndEvent_Id(dto.memberId(), dto.eventId())){
            throw new PresenceConflictException("Presence already registered");
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

        return presenceMapper.entityToRegisterPresenceRDTO(savedPresenceEntity);
    }
}
