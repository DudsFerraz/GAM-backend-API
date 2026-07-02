package br.org.gam.api.event.Missa.application.useCases.CreateMissa;

import br.org.gam.api.event.application.useCases.CreateEvent.CreateEvent;
import br.org.gam.api.event.application.useCases.CreateEvent.CreateEventDTO;
import br.org.gam.api.event.application.useCases.CreateEvent.CreateEventRDTO;
import br.org.gam.api.event.application.useCases.GetEventInstance.GetEventInstance;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.event.Missa.application.MissaMapper;
import br.org.gam.api.event.Missa.domain.Missa;
import br.org.gam.api.event.Missa.persistence.MissaEntity;
import br.org.gam.api.event.Missa.persistence.MissaRepository;
import br.org.gam.api.member.application.useCases.GetMemberInstance.GetMemberInstance;
import br.org.gam.api.member.domain.Member;
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
@DisplayName("Create Missa Use Case")
class SpringCreateMissaTest {

    @Mock
    private CreateEvent createEventService;

    @Mock
    private GetEventInstance getEventInstanceService;

    @Mock
    private GetMemberInstance getMemberInstanceService;

    @Mock
    private MissaMapper missaMapper;

    @Mock
    private MissaRepository missaRepo;

    @InjectMocks
    private SpringCreateMissa createMissa;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - valid missa data -> missa is created")
        void validMissaDataShouldCreateMissa() {
            UUID eventId = UUID.randomUUID();
            UUID comentariosId = UUID.randomUUID();
            UUID acolhidaId = UUID.randomUUID();
            CreateMissaDTO dto = new CreateMissaDTO(eventDto(), comentariosId, null, null, null, null, Set.of(acolhidaId));
            Event event = event();
            Member comentarios = mock(Member.class);
            Member acolhida = mock(Member.class);
            MissaEntity mappedEntity = new MissaEntity();
            MissaEntity savedEntity = new MissaEntity();
            CreateMissaRDTO expectedResponse = new CreateMissaRDTO(eventId);

            when(createEventService.create(dto.event())).thenReturn(new CreateEventRDTO(eventId));
            when(getEventInstanceService.domainById(eventId)).thenReturn(event);
            when(getMemberInstanceService.domainById(comentariosId)).thenReturn(comentarios);
            when(getMemberInstanceService.domainsById(dto.acolhidaMembersIds())).thenReturn(Set.of(acolhida));
            when(missaMapper.domainToEntity(any(Missa.class))).thenReturn(mappedEntity);
            when(missaRepo.save(mappedEntity)).thenReturn(savedEntity);
            when(missaMapper.entityToCreateMissaRDTO(savedEntity)).thenReturn(expectedResponse);

            CreateMissaRDTO response = createMissa.createMissa(dto);

            assertThat(response).isSameAs(expectedResponse);

            ArgumentCaptor<Missa> missaCaptor = ArgumentCaptor.forClass(Missa.class);
            verify(missaMapper).domainToEntity(missaCaptor.capture());
            Missa missa = missaCaptor.getValue();
            assertThat(missa.getId()).isEqualTo(event.getId());
            assertThat(missa.getEvent()).isSameAs(event);
            assertThat(missa.getComentariosMember()).isSameAs(comentarios);
            assertThat(missa.getLeitura1Member()).isNull();
            assertThat(missa.getAcolhidaMembers()).containsExactly(acolhida);
            verify(missaRepo).save(mappedEntity);
        }
    }

    private static CreateEventDTO eventDto() {
        Instant beginDate = Instant.now().plusSeconds(3600);
        return new CreateEventDTO("Missa", null, UUID.randomUUID(), UUID.randomUUID(), beginDate, beginDate.plusSeconds(3600), EventType.MISSA);
    }

    private static Event event() {
        Instant beginDate = Instant.now().plusSeconds(3600);
        return Event.register("Missa", null, beginDate, beginDate.plusSeconds(3600), EventType.MISSA);
    }
}
