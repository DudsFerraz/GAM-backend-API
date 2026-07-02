package br.org.gam.api.event.Oratorio.application.useCases.CreateOratorio;

import br.org.gam.api.event.application.useCases.CreateEvent.CreateEvent;
import br.org.gam.api.event.application.useCases.CreateEvent.CreateEventRDTO;
import br.org.gam.api.event.application.useCases.GetEventInstance.GetEventInstance;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.Oratorio.application.OratorioMapper;
import br.org.gam.api.event.Oratorio.domain.Oratorio;
import br.org.gam.api.event.Oratorio.persistence.OratorioEntity;
import br.org.gam.api.event.Oratorio.persistence.OratorioRepository;
import br.org.gam.api.member.application.useCases.GetMemberInstance.GetMemberInstance;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.oratoriano.application.useCases.GetOratorianoInstance.GetOratorianoInstance;
import br.org.gam.api.oratoriano.domain.Oratoriano;
import jakarta.transaction.Transactional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Transactional
@Service
public class CreateOratorio {
    private final OratorioRepository oratorioRepo;
    private final CreateEvent createEventService;
    private final GetEventInstance getEventInstanceService;
    private final GetMemberInstance getMemberInstanceService;
    private final GetOratorianoInstance getOratorianoInstanceService;
    private final OratorioMapper oratorioMapper;

    public CreateOratorio(OratorioRepository oratorioRepo, CreateEvent createEventService, GetEventInstance getEventInstanceService, GetMemberInstance getMemberInstanceService, GetOratorianoInstance getOratorianoInstanceService, OratorioMapper oratorioMapper) {
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
        Event newEvent = getEventInstanceService.domainById(newEventRdto.id());

        Set<Member> lancheMembers = getMemberInstanceService.domainsById(dto.lancheMembersIds());
        Set<Member> btJovensMembers = getMemberInstanceService.domainsById(dto.btJovensMembersIds());
        Set<Member> btCriancasMembers = getMemberInstanceService.domainsById(dto.btCriancasMembersIds());
        Set<Oratoriano> oratorianos = getOratorianoInstanceService.domainsbyId(dto.oratorianosIds());

        Oratorio newOratorio = Oratorio.register(newEvent, lancheMembers, btJovensMembers, btCriancasMembers, oratorianos);

        OratorioEntity newOratorioEntity = oratorioMapper.domainToEntity(newOratorio);
        OratorioEntity savedOratorioEntity = oratorioRepo.save(newOratorioEntity);

        return oratorioMapper.entityToCreateOratorioRDTO(savedOratorioEntity);
    }
}
