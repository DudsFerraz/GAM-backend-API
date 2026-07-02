package br.org.gam.api.event.Oratorio.application.useCases.CreateOratorio;

import br.org.gam.api.event.application.useCases.CreateEvent.CreateEvent;
import br.org.gam.api.event.application.useCases.CreateEvent.CreateEventRDTO;
import br.org.gam.api.event.application.EventDomainLoader;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.Oratorio.application.OratorioMapper;
import br.org.gam.api.event.Oratorio.domain.Oratorio;
import br.org.gam.api.event.Oratorio.persistence.OratorioEntity;
import br.org.gam.api.event.Oratorio.persistence.OratorioRepository;
import br.org.gam.api.member.application.MemberDomainLoader;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.oratoriano.application.OratorianoDomainLoader;
import br.org.gam.api.oratoriano.domain.Oratoriano;
import jakarta.transaction.Transactional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Transactional
@Service
public class CreateOratorio {
    private final OratorioRepository oratorioRepo;
    private final CreateEvent createEventService;
    private final EventDomainLoader getEventInstanceService;
    private final MemberDomainLoader getMemberInstanceService;
    private final OratorianoDomainLoader getOratorianoInstanceService;
    private final OratorioMapper oratorioMapper;

    public CreateOratorio(OratorioRepository oratorioRepo, CreateEvent createEventService, EventDomainLoader getEventInstanceService, MemberDomainLoader getMemberInstanceService, OratorianoDomainLoader getOratorianoInstanceService, OratorioMapper oratorioMapper) {
        this.oratorioRepo = oratorioRepo;
        this.createEventService = createEventService;
        this.getEventInstanceService = getEventInstanceService;
        this.getMemberInstanceService = getMemberInstanceService;
        this.getOratorianoInstanceService = getOratorianoInstanceService;
        this.oratorioMapper = oratorioMapper;
    }

    @Transactional
    public CreateOratorioRDTO create(CreateOratorioDTO dto) {
        CreateEventRDTO newEventRdto = createEventService.create(dto.event());
        Event newEvent = getEventInstanceService.requiredById(newEventRdto.id());

        Set<Member> lancheMembers = getMemberInstanceService.requiredByIds(dto.lancheMembersIds());
        Set<Member> btJovensMembers = getMemberInstanceService.requiredByIds(dto.btJovensMembersIds());
        Set<Member> btCriancasMembers = getMemberInstanceService.requiredByIds(dto.btCriancasMembersIds());
        Set<Oratoriano> oratorianos = getOratorianoInstanceService.requiredByIds(dto.oratorianosIds());

        Oratorio newOratorio = Oratorio.register(newEvent, lancheMembers, btJovensMembers, btCriancasMembers, oratorianos);

        OratorioEntity newOratorioEntity = oratorioMapper.domainToEntity(newOratorio);
        OratorioEntity savedOratorioEntity = oratorioRepo.save(newOratorioEntity);

        return oratorioMapper.entityToCreateOratorioRDTO(savedOratorioEntity);
    }
}
