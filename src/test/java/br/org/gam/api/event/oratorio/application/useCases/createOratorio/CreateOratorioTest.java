package br.org.gam.api.event.oratorio.application.useCases.createOratorio;

import br.org.gam.api.event.application.useCases.createEvent.CreateEvent;
import br.org.gam.api.event.application.useCases.createEvent.CreateEventDTO;
import br.org.gam.api.event.application.useCases.createEvent.CreateEventRDTO;
import br.org.gam.api.event.application.EventDomainLoader;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.event.oratorio.application.OratorioMapper;
import br.org.gam.api.event.oratorio.domain.Oratorio;
import br.org.gam.api.event.oratorio.persistence.OratorioEntity;
import br.org.gam.api.event.oratorio.persistence.OratorioRepository;
import br.org.gam.api.member.application.MemberDomainLoader;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.oratoriano.application.OratorianoDomainLoader;
import br.org.gam.api.oratoriano.domain.Oratoriano;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Create Oratorio Use Case")
class CreateOratorioTest {

    @Mock
    private OratorioRepository oratorioRepo;

    @Mock
    private CreateEvent createEventService;

    @Mock
    private EventDomainLoader getEventInstanceService;

    @Mock
    private MemberDomainLoader getMemberInstanceService;

    @Mock
    private OratorianoDomainLoader getOratorianoInstanceService;

    @Mock
    private OratorioMapper oratorioMapper;

    @Mock
    private ActivityEvents activityEvents;

    @InjectMocks
    private CreateOratorio createOratorio;

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

            when(createEventService.create(dto.event(), false)).thenReturn(new CreateEventRDTO(eventId));
            when(getEventInstanceService.requiredById(eventId)).thenReturn(event);
            when(getMemberInstanceService.requiredByIds(dto.lancheMembersIds())).thenReturn(Set.of(lanche));
            when(getMemberInstanceService.requiredByIds(dto.btJovensMembersIds())).thenReturn(Set.of());
            when(getMemberInstanceService.requiredByIds(dto.btCriancasMembersIds())).thenReturn(Set.of());
            when(getOratorianoInstanceService.requiredByIds(dto.oratorianosIds())).thenReturn(Set.of(oratoriano));
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
            verify(activityEvents).oratorioCreated(oratorio.getId(), event.getId());
        }
    }

    private static CreateEventDTO eventDto() {
        Instant beginDate = Instant.now().plusSeconds(3600);
        return new CreateEventDTO("Oratorio", null, UUID.randomUUID(), UUID.randomUUID(), beginDate, beginDate.plusSeconds(3600), EventType.ORATORIO);
    }

    private static Event event() {
        Instant beginDate = Instant.now().plusSeconds(3600);
        return Event.register("Oratorio", null, beginDate, beginDate.plusSeconds(3600), EventType.ORATORIO);
    }
}
