package br.org.gam.api.Entities.events.oratorio.services;

import br.org.gam.api.Entities.events.generic.Event;
import br.org.gam.api.Entities.events.generic.EventType;
import br.org.gam.api.Entities.events.generic.services.createEvent.CreateEvent;
import br.org.gam.api.Entities.events.generic.services.createEvent.CreateEventDTO;
import br.org.gam.api.Entities.events.generic.services.createEvent.CreateEventRDTO;
import br.org.gam.api.Entities.events.generic.services.getEventInstance.GetEventInstance;
import br.org.gam.api.Entities.events.oratorio.Oratorio;
import br.org.gam.api.Entities.events.oratorio.OratorioMapper;
import br.org.gam.api.Entities.events.oratorio.persistence.OratorioEntity;
import br.org.gam.api.Entities.events.oratorio.persistence.OratorioRepository;
import br.org.gam.api.Entities.events.oratorio.services.createOratorio.CreateOratorioDTO;
import br.org.gam.api.Entities.events.oratorio.services.createOratorio.CreateOratorioRDTO;
import br.org.gam.api.Entities.events.oratorio.services.createOratorio.SpringCreateOratorio;
import br.org.gam.api.Entities.member.Member;
import br.org.gam.api.Entities.member.services.getMemberInstance.GetMemberInstance;
import br.org.gam.api.Entities.oratoriano.Oratoriano;
import br.org.gam.api.Entities.oratoriano.services.getOratorianoInstance.GetOratorianoInstance;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Create Oratorio Use Case")
class SpringCreateOratorioTest {

    @Mock
    private OratorioRepository oratorioRepo;

    @Mock
    private CreateEvent createEventService;

    @Mock
    private GetEventInstance getEventInstanceService;

    @Mock
    private GetMemberInstance getMemberInstanceService;

    @Mock
    private GetOratorianoInstance getOratorianoInstanceService;

    @Mock
    private OratorioMapper oratorioMapper;

    @InjectMocks
    private SpringCreateOratorio createOratorio;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - valid oratorio data -> oratorio is created")
        void validOratorioDataShouldCreateOratorio() {
            UUID eventId = UUID.randomUUID();
            UUID lancheId = UUID.randomUUID();
            UUID oratorianoId = UUID.randomUUID();
            CreateOratorioDTO dto = new CreateOratorioDTO(eventDto(), Set.of(lancheId), Set.of(), Set.of(), Set.of(oratorianoId));
            Event event = event();
            Member lanche = mock(Member.class);
            Oratoriano oratoriano = mock(Oratoriano.class);
            OratorioEntity mappedEntity = new OratorioEntity();
            OratorioEntity savedEntity = new OratorioEntity();
            CreateOratorioRDTO expectedResponse = new CreateOratorioRDTO(eventId);

            when(createEventService.create(dto.event())).thenReturn(new CreateEventRDTO(eventId));
            when(getEventInstanceService.domainById(eventId)).thenReturn(event);
            when(getMemberInstanceService.domainsById(dto.lancheMembersIds())).thenReturn(Set.of(lanche));
            when(getMemberInstanceService.domainsById(dto.btJovensMembersIds())).thenReturn(Set.of());
            when(getMemberInstanceService.domainsById(dto.btCriancasMembersIds())).thenReturn(Set.of());
            when(getOratorianoInstanceService.domainsbyId(dto.oratorianosIds())).thenReturn(Set.of(oratoriano));
            when(oratorioMapper.domainToEntity(any(Oratorio.class))).thenReturn(mappedEntity);
            when(oratorioRepo.save(mappedEntity)).thenReturn(savedEntity);
            when(oratorioMapper.entityToCreateOratorioRDTO(savedEntity)).thenReturn(expectedResponse);

            CreateOratorioRDTO response = createOratorio.create(dto);

            assertThat(response).isSameAs(expectedResponse);

            ArgumentCaptor<Oratorio> oratorioCaptor = ArgumentCaptor.forClass(Oratorio.class);
            verify(oratorioMapper).domainToEntity(oratorioCaptor.capture());
            Oratorio oratorio = oratorioCaptor.getValue();
            assertThat(oratorio.getId()).isEqualTo(event.getId());
            assertThat(oratorio.getEvent()).isSameAs(event);
            assertThat(oratorio.getLancheMembers()).containsExactly(lanche);
            assertThat(oratorio.getBtJovensMembers()).isEmpty();
            assertThat(oratorio.getBtCriancasMembers()).isEmpty();
            assertThat(oratorio.getOratorianos()).containsExactly(oratoriano);
            verify(oratorioRepo).save(mappedEntity);
        }
    }

    private static CreateEventDTO eventDto() {
        Instant beginDate = Instant.now().plusSeconds(3600);
        return new CreateEventDTO("Oratorio", null, UUID.randomUUID(), UUID.randomUUID(), beginDate, beginDate.plusSeconds(3600), EventType.ORATORIO);
    }

    private static Event event() {
        Instant beginDate = Instant.now().plusSeconds(3600);
        return Event.register("Oratorio", null, null, null, beginDate, beginDate.plusSeconds(3600), EventType.ORATORIO);
    }
}
