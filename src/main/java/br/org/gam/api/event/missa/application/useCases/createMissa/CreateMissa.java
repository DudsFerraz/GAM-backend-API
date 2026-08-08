package br.org.gam.api.event.missa.application.useCases.createMissa;

import br.org.gam.api.event.application.useCases.createEvent.CreateEvent;
import br.org.gam.api.event.application.useCases.createEvent.CreateEventRDTO;
import br.org.gam.api.event.application.EventDomainLoader;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.missa.application.MissaMapper;
import br.org.gam.api.event.missa.domain.Missa;
import br.org.gam.api.event.missa.persistence.MissaEntity;
import br.org.gam.api.event.missa.persistence.MissaRepository;
import br.org.gam.api.member.application.MemberDomainLoader;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import jakarta.transaction.Transactional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Transactional
@Service
public class CreateMissa {
    private final CreateEvent createEventService;
    private final EventDomainLoader getEventInstanceService;
    private final MemberDomainLoader getMemberInstanceService;
    private final MissaMapper missaMapper;
    private final MissaRepository missaRepo;
    private final ActivityEvents activityEvents;

    public CreateMissa(CreateEvent createEventService, EventDomainLoader getEventInstanceService,
                       MemberDomainLoader getMemberInstanceService, MissaMapper missaMapper,
                       MissaRepository missaRepo, ActivityEvents activityEvents) {
        this.createEventService = createEventService;
        this.getEventInstanceService = getEventInstanceService;
        this.getMemberInstanceService = getMemberInstanceService;
        this.missaMapper = missaMapper;
        this.missaRepo = missaRepo;
        this.activityEvents = activityEvents;
    }

    @Transactional
    public CreateMissaRDTO createMissa(CreateMissaDTO dto) {
        CreateEventRDTO newEventRdto = createEventService.create(dto.event(), false);
        Event newEvent = getEventInstanceService.requiredById(newEventRdto.id());


        Member comentariosMember = resolveMember(dto.comentariosMemberId());
        Member leitura1Member = resolveMember(dto.leitura1MemberId());
        Member salmoMember = resolveMember(dto.salmoMemberId());
        Member leitura2Member = resolveMember(dto.leitura2MemberId());
        Member precesMember = resolveMember(dto.precesMemberId());
        Set<Member> acolhidaMembers = getMemberInstanceService.requiredByIds(dto.acolhidaMembersIds());

        Missa newMissa = Missa.register(newEvent, comentariosMember, leitura1Member, salmoMember,  leitura2Member, precesMember, acolhidaMembers);

        MissaEntity newEntity = missaMapper.domainToEntity(newMissa);
        MissaEntity savedEntity = missaRepo.save(newEntity);

        activityEvents.missaCreated(
                newMissa.getId(),
                newEvent.getId()
        );

        return missaMapper.entityToCreateMissaRDTO(savedEntity);
    }

    private Member resolveMember(UUID memberId) {
        if (memberId == null) return null;
        return getMemberInstanceService.requiredById(memberId);
    }

}
