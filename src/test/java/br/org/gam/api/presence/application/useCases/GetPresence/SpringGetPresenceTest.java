package br.org.gam.api.presence.application.useCases.GetPresence;

import br.org.gam.api.event.application.EventNotFoundException;
import br.org.gam.api.event.application.EventSecurity;
import br.org.gam.api.event.application.useCases.GetEventInstance.GetEventInstance;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.presence.application.PresenceMapper;
import br.org.gam.api.presence.application.PresenceNotFoundException;
import br.org.gam.api.presence.application.PresenceRDTO;
import br.org.gam.api.presence.persistence.PresenceEntity;
import br.org.gam.api.presence.persistence.PresenceRepository;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Get Presence Use Case")
@SuppressWarnings("unchecked")
class SpringGetPresenceTest {

    @Mock
    private PresenceMapper presenceMapper;

    @Mock
    private PresenceRepository presenceRepo;

    @Mock
    private EventSecurity eventSecurity;

    @Mock
    private GetEventInstance getEventInstance;

    @InjectMocks
    private SpringGetPresence getPresence;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - registered member in event -> presence response")
        void registeredMemberInEventShouldReturnPresenceResponse() {
            UUID memberId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            PresenceEntity entity = new PresenceEntity();
            PresenceRDTO expectedResponse = response(UUID.randomUUID(), "Checked in");

            when(presenceRepo.findOne(any(Specification.class))).thenReturn(Optional.of(entity));
            when(presenceMapper.entityToPresenceRDTO(entity)).thenReturn(expectedResponse);

            PresenceRDTO response = getPresence.byIds(memberId, eventId);

            assertThat(response).isSameAs(expectedResponse);

            ArgumentCaptor<Specification<PresenceEntity>> specificationCaptor = ArgumentCaptor.forClass(Specification.class);
            verify(presenceRepo).findOne(specificationCaptor.capture());
            assertThat(specificationCaptor.getValue()).isNotNull();
            verify(presenceMapper).entityToPresenceRDTO(entity);
        }

        @Test
        @DisplayName("EP - member without presence in event -> not found error")
        void memberWithoutPresenceInEventShouldReturnNotFoundError() {
            UUID memberId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();

            when(presenceRepo.findOne(any(Specification.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getPresence.byIds(memberId, eventId))
                    .isInstanceOf(PresenceNotFoundException.class)
                    .hasMessage("member with id: " + memberId + " has no presence registered in event with id: " + eventId);

            verifyNoInteractions(presenceMapper, eventSecurity, getEventInstance);
        }

        @Test
        @DisplayName("EP - visible event id -> mapped presence page")
        void visibleEventIdShouldReturnMappedPresencePage() {
            UUID eventId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 10);
            EventEntity eventEntity = new EventEntity();
            PresenceEntity firstEntity = new PresenceEntity();
            PresenceEntity secondEntity = new PresenceEntity();
            PresenceRDTO firstResponse = response(UUID.randomUUID(), "First");
            PresenceRDTO secondResponse = response(UUID.randomUUID(), "Second");

            when(getEventInstance.entityById(eventId)).thenReturn(eventEntity);
            when(eventSecurity.canGetEvent(eventEntity)).thenReturn(true);
            when(presenceRepo.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(firstEntity, secondEntity), pageable, 2));
            when(presenceMapper.entityToPresenceRDTO(firstEntity)).thenReturn(firstResponse);
            when(presenceMapper.entityToPresenceRDTO(secondEntity)).thenReturn(secondResponse);

            Page<PresenceRDTO> response = getPresence.allByEvent(eventId, pageable);

            assertThat(response.getContent()).containsExactly(firstResponse, secondResponse);
            assertThat(response.getTotalElements()).isEqualTo(2);

            ArgumentCaptor<Specification<PresenceEntity>> specificationCaptor = ArgumentCaptor.forClass(Specification.class);
            verify(presenceRepo).findAll(specificationCaptor.capture(), eq(pageable));
            assertThat(specificationCaptor.getValue()).isNotNull();
            verify(eventSecurity).canGetEvent(eventEntity);
            verify(presenceMapper).entityToPresenceRDTO(firstEntity);
            verify(presenceMapper).entityToPresenceRDTO(secondEntity);
        }

        @Test
        @DisplayName("EP - hidden event id -> not found error")
        void hiddenEventIdShouldReturnNotFoundError() {
            UUID eventId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 10);
            EventEntity eventEntity = new EventEntity();

            when(getEventInstance.entityById(eventId)).thenReturn(eventEntity);
            when(eventSecurity.canGetEvent(eventEntity)).thenReturn(false);

            assertThatThrownBy(() -> getPresence.allByEvent(eventId, pageable))
                    .isInstanceOf(EventNotFoundException.class)
                    .hasMessage("Could not find event with id " + eventId);

            verify(eventSecurity).canGetEvent(eventEntity);
            verifyNoInteractions(presenceRepo, presenceMapper);
        }

        @Test
        @DisplayName("EP - missing event id -> not found error")
        void missingEventIdShouldReturnNotFoundError() {
            UUID eventId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 10);

            when(getEventInstance.entityById(eventId))
                    .thenThrow(new EventNotFoundException("Could not find event with id " + eventId));

            assertThatThrownBy(() -> getPresence.allByEvent(eventId, pageable))
                    .isInstanceOf(EventNotFoundException.class)
                    .hasMessage("Could not find event with id " + eventId);

            verifyNoInteractions(eventSecurity, presenceRepo, presenceMapper);
        }

        @Test
        @DisplayName("EP - member id -> mapped presence page")
        void memberIdShouldReturnMappedPresencePage() {
            UUID memberId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 10);
            PresenceEntity entity = new PresenceEntity();
            PresenceRDTO expectedResponse = response(UUID.randomUUID(), "Confirmed");

            when(presenceRepo.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(entity), pageable, 1));
            when(presenceMapper.entityToPresenceRDTO(entity)).thenReturn(expectedResponse);

            Page<PresenceRDTO> response = getPresence.allByMember(memberId, pageable);

            assertThat(response.getContent()).containsExactly(expectedResponse);
            assertThat(response.getTotalElements()).isEqualTo(1);

            ArgumentCaptor<Specification<PresenceEntity>> specificationCaptor = ArgumentCaptor.forClass(Specification.class);
            verify(presenceRepo).findAll(specificationCaptor.capture(), eq(pageable));
            assertThat(specificationCaptor.getValue()).isNotNull();
            verify(presenceMapper).entityToPresenceRDTO(entity);
        }
    }

    @Nested
    @StructuralTest
    @DisplayName("Structural")
    class Structural {

        @Test
        @DisplayName("visible event with empty presence page -> empty mapped page")
        void visibleEventWithEmptyPresencePageShouldReturnEmptyMappedPage() {
            UUID eventId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 10);
            EventEntity eventEntity = new EventEntity();

            when(getEventInstance.entityById(eventId)).thenReturn(eventEntity);
            when(eventSecurity.canGetEvent(eventEntity)).thenReturn(true);
            when(presenceRepo.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty(pageable));

            Page<PresenceRDTO> response = getPresence.allByEvent(eventId, pageable);

            assertThat(response.getContent()).isEmpty();
            assertThat(response.getTotalElements()).isZero();
            verifyNoInteractions(presenceMapper);
        }

        @Test
        @DisplayName("member with empty presence page -> empty mapped page")
        void memberWithEmptyPresencePageShouldReturnEmptyMappedPage() {
            UUID memberId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 10);

            when(presenceRepo.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty(pageable));

            Page<PresenceRDTO> response = getPresence.allByMember(memberId, pageable);

            assertThat(response.getContent()).isEmpty();
            assertThat(response.getTotalElements()).isZero();
            verifyNoInteractions(presenceMapper, eventSecurity, getEventInstance);
        }
    }

    private static PresenceRDTO response(UUID id, String observations) {
        return new PresenceRDTO(id, null, null, observations);
    }
}
