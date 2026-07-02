package br.org.gam.api.presence.application.useCases.RegisterPresence;

import br.org.gam.api.event.application.useCases.GetEventInstance.GetEventInstance;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.member.application.useCases.GetMemberInstance.GetMemberInstance;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.presence.application.PresenceConflictException;
import br.org.gam.api.presence.application.PresenceMapper;
import br.org.gam.api.presence.domain.Presence;
import br.org.gam.api.presence.persistence.PresenceEntity;
import br.org.gam.api.presence.persistence.PresenceRepository;
import org.springframework.stereotype.Service;

@Service
public class SpringRegisterPresence implements RegisterPresence {
    private final PresenceRepository presenceRepo;
    private final PresenceMapper presenceMapper;
    private final GetMemberInstance getMemberInstance;
    private final GetEventInstance getEventInstance;

    public SpringRegisterPresence(PresenceRepository presenceRepo, PresenceMapper presenceMapper, GetMemberInstance getMemberInstance, GetEventInstance getEventInstance) {
        this.presenceRepo = presenceRepo;
        this.presenceMapper = presenceMapper;
        this.getMemberInstance = getMemberInstance;
        this.getEventInstance = getEventInstance;
    }

    @Override
    public RegisterPresenceRDTO register(RegisterPresenceDTO dto) {
        if(presenceRepo.existsByMember_IdAndEvent_Id(dto.memberId(), dto.eventId())){
            throw new PresenceConflictException("Presence already registered");
        }

        Member presentMember = getMemberInstance.domainById(dto.memberId());
        Event relatedEvent = getEventInstance.domainById(dto.eventId());

        Presence newPresence = Presence.register(presentMember, relatedEvent, dto.observations());
        PresenceEntity newPresenceEntity = presenceMapper.domainToEntity(newPresence);
        PresenceEntity savedPresenceEntity = presenceRepo.save(newPresenceEntity);

        return presenceMapper.entityToRegisterPresenceRDTO(savedPresenceEntity);
    }
}
