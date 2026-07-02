package br.org.gam.api.event.Missa.application.useCases.CreateMissa;

import br.org.gam.api.event.application.useCases.CreateEvent.CreateEvent;
import br.org.gam.api.event.application.useCases.CreateEvent.CreateEventRDTO;
import br.org.gam.api.event.application.EventDomainLoader;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.Missa.application.MissaMapper;
import br.org.gam.api.event.Missa.domain.Missa;
import br.org.gam.api.event.Missa.persistence.MissaEntity;
import br.org.gam.api.event.Missa.persistence.MissaRepository;
import br.org.gam.api.member.application.MemberDomainLoader;
import br.org.gam.api.member.domain.Member;
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
    public CreateMissa(CreateEvent createEventService, EventDomainLoader getEventInstanceService, MemberDomainLoader getMemberInstanceService, MissaMapper missaMapper, MissaRepository missaRepo) {
        this.createEventService = createEventService;
        this.getEventInstanceService = getEventInstanceService;
        this.getMemberInstanceService = getMemberInstanceService;
        this.missaMapper = missaMapper;
        this.missaRepo = missaRepo;
    }

    @Transactional
    public CreateMissaRDTO createMissa(CreateMissaDTO dto) {
        CreateEventRDTO newEventRdto = createEventService.create(dto.event());
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

        return missaMapper.entityToCreateMissaRDTO(savedEntity);
    }

    private Member resolveMember(UUID memberId) {
        if (memberId == null) return null;
        return getMemberInstanceService.requiredById(memberId);
    }

}
