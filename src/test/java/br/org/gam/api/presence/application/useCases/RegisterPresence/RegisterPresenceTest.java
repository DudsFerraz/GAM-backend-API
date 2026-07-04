package br.org.gam.api.presence.application.useCases.RegisterPresence;

import br.org.gam.api.event.application.EventNotFoundException;
import br.org.gam.api.event.application.EventEntityLoader;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.member.application.MemberNotFoundException;
import br.org.gam.api.member.application.MemberEntityLoader;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.presence.application.PresenceConflictException;
import br.org.gam.api.presence.application.PresenceMapper;
import br.org.gam.api.presence.persistence.PresenceEntity;
import br.org.gam.api.presence.persistence.PresenceRepository;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Register Presence Use Case")
class RegisterPresenceTest {

    @Mock
    private PresenceRepository presenceRepo;

    @Mock
    private PresenceMapper presenceMapper;

    @Mock
    private MemberEntityLoader getMemberInstance;

    @Mock
    private EventEntityLoader getEventInstance;

    @Mock
    private ActivityEvents activityEvents;

    @InjectMocks
    private RegisterPresence registerPresence;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - unregistered member in event -> presence is registered")
        void unregisteredMemberInEventShouldRegisterPresence() {
            UUID memberId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            RegisterPresenceDTO dto = new RegisterPresenceDTO(eventId, memberId, "  Present at entrance  ");
            MemberEntity member = new MemberEntity();
            EventEntity event = new EventEntity();
            PresenceEntity savedEntity = new PresenceEntity();
            RegisterPresenceRDTO expectedResponse = new RegisterPresenceRDTO(UUID.randomUUID());

            when(presenceRepo.existsByMember_IdAndEvent_Id(memberId, eventId)).thenReturn(false);
            when(getMemberInstance.requiredById(memberId)).thenReturn(member);
            when(getEventInstance.requiredById(eventId)).thenReturn(event);
            when(presenceRepo.save(anyPresenceEntity())).thenReturn(savedEntity);
            when(presenceMapper.entityToRegisterPresenceRDTO(savedEntity)).thenReturn(expectedResponse);

            RegisterPresenceRDTO response = registerPresence.register(dto);

            assertThat(response).isSameAs(expectedResponse);

            ArgumentCaptor<PresenceEntity> presenceCaptor = ArgumentCaptor.forClass(PresenceEntity.class);
            verify(presenceRepo).save(presenceCaptor.capture());
            PresenceEntity presence = presenceCaptor.getValue();

            assertThat(presence.getId()).isNotNull();
            assertThat(presence.getId().version()).isEqualTo(7);
            assertThat(presence.getMember()).isSameAs(member);
            assertThat(presence.getEvent()).isSameAs(event);
            assertThat(presence.getObservations()).isEqualTo("Present at entrance");
            verify(activityEvents).presenceRegistered(presence.getId(), memberId, eventId);
        }

        @Test
        @DisplayName("EP - already registered member in event -> conflict error")
        void alreadyRegisteredMemberInEventShouldReturnConflictError() {
            UUID memberId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            RegisterPresenceDTO dto = new RegisterPresenceDTO(eventId, memberId, null);

            when(presenceRepo.existsByMember_IdAndEvent_Id(memberId, eventId)).thenReturn(true);

            assertThatThrownBy(() -> registerPresence.register(dto))
                    .isInstanceOf(PresenceConflictException.class)
                    .hasMessage("Presence already registered");

            verifyNoInteractions(getMemberInstance, getEventInstance, presenceMapper);
            verify(presenceRepo, never()).save(any());
        }

        @Test
        @DisplayName("EP - missing member id -> not found error")
        void missingMemberIdShouldReturnNotFoundError() {
            UUID memberId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            RegisterPresenceDTO dto = new RegisterPresenceDTO(eventId, memberId, null);

            when(presenceRepo.existsByMember_IdAndEvent_Id(memberId, eventId)).thenReturn(false);
            when(getMemberInstance.requiredById(memberId))
                    .thenThrow(new MemberNotFoundException("Could not find member with id " + memberId));

            assertThatThrownBy(() -> registerPresence.register(dto))
                    .isInstanceOf(MemberNotFoundException.class)
                    .hasMessage("Could not find member with id " + memberId);

            verifyNoInteractions(getEventInstance, presenceMapper);
            verify(presenceRepo, never()).save(any());
        }

        @Test
        @DisplayName("EP - missing event id -> not found error")
        void missingEventIdShouldReturnNotFoundError() {
            UUID memberId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            RegisterPresenceDTO dto = new RegisterPresenceDTO(eventId, memberId, null);
            MemberEntity member = new MemberEntity();

            when(presenceRepo.existsByMember_IdAndEvent_Id(memberId, eventId)).thenReturn(false);
            when(getMemberInstance.requiredById(memberId)).thenReturn(member);
            when(getEventInstance.requiredById(eventId))
                    .thenThrow(new EventNotFoundException("Could not find event with id " + eventId));

            assertThatThrownBy(() -> registerPresence.register(dto))
                    .isInstanceOf(EventNotFoundException.class)
                    .hasMessage("Could not find event with id " + eventId);

            verifyNoInteractions(presenceMapper);
            verify(presenceRepo, never()).save(any());
        }
    }

    private static PresenceEntity anyPresenceEntity() {
        return org.mockito.ArgumentMatchers.any(PresenceEntity.class);
    }
}
