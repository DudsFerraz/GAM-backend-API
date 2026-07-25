package br.org.gam.api.presence.application.useCases.registerPresence;

import br.org.gam.api.event.application.EventEntityLoader;
import br.org.gam.api.event.application.EventSecurity;
import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.member.application.MemberEntityLoader;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.presence.application.PresenceMapper;
import br.org.gam.api.presence.persistence.PresenceEntity;
import br.org.gam.api.presence.persistence.PresenceRepository;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.domain.GamName;
import br.org.gam.api.shared.exception.ConflictException;
import br.org.gam.api.shared.exception.NotFoundException;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
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

    @Mock
    private EventSecurity eventSecurity;

    @Mock
    private PresenceConflictResolver conflictResolver;

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
            EventEntity event = registrationEligibleEvent();
            PresenceEntity savedEntity = new PresenceEntity();
            RegisterPresenceRDTO expectedResponse = mock(RegisterPresenceRDTO.class);

            when(getEventInstance.requiredByIdForUpdate(eventId)).thenReturn(event);
            when(eventSecurity.canGetEvent(event)).thenReturn(true);
            when(presenceRepo.existsByMember_IdAndEvent_Id(memberId, eventId)).thenReturn(false);
            when(getMemberInstance.requiredById(memberId)).thenReturn(member);
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
            assertThat(mockingDetails(activityEvents).getInvocations())
                    .singleElement()
                    .satisfies(invocation -> {
                        assertThat(invocation.getMethod().getName()).isEqualTo("presenceRegistered");
                        assertThat(invocation.getArguments())
                                .contains(presence.getId(), memberId, eventId);
                    });
        }

        @Test
        @DisplayName("REQ-EVENT-006 and REQ-PRESENCE-007 - response status reuses the registration evaluation instant")
        void responseStatusShouldReuseRegistrationEvaluationInstant() {
            Instant evaluationInstant = Instant.parse("2030-01-01T10:00:00Z");
            Instant eventEnd = evaluationInstant.plusMillis(1);
            Instant laterMappingInstant = eventEnd.plusMillis(1);
            UUID memberId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            EventEntity event = new EventEntity();
            event.setId(eventId);
            event.setTitle("Single clock Presence");
            event.setType(EventType.GENERIC);
            event.setStatus(EventStatus.SCHEDULED);
            event.setBeginDate(evaluationInstant.minusSeconds(3_600));
            event.setEndDate(eventEnd);
            MemberEntity member = new MemberEntity();
            member.setId(memberId);
            member.setName(new GamName("Ana", "Silva"));
            member.setStatus(MemberStatus.ACTIVE);
            PresenceMapper realMapper = mock(PresenceMapper.class, Answers.CALLS_REAL_METHODS);
            RegisterPresence useCase = new RegisterPresence(
                    presenceRepo,
                    realMapper,
                    getMemberInstance,
                    getEventInstance,
                    activityEvents,
                    eventSecurity
            );

            when(getEventInstance.requiredByIdForUpdate(eventId)).thenReturn(event);
            when(eventSecurity.canGetEvent(event)).thenReturn(true);
            when(presenceRepo.existsByMember_IdAndEvent_Id(memberId, eventId)).thenReturn(false);
            when(getMemberInstance.requiredById(memberId)).thenReturn(member);
            when(presenceRepo.save(any(PresenceEntity.class))).thenAnswer(invocation -> {
                PresenceEntity saved = invocation.getArgument(0);
                saved.setCreatedAt(evaluationInstant);
                return saved;
            });

            RegisterPresenceRDTO response;
            AtomicInteger clockReads = new AtomicInteger();
            try (MockedStatic<Instant> instant = Mockito.mockStatic(Instant.class, Answers.CALLS_REAL_METHODS)) {
                instant.when(Instant::now).thenAnswer(ignored ->
                        clockReads.getAndIncrement() == 0 ? evaluationInstant : laterMappingInstant
                );
                response = useCase.register(new RegisterPresenceDTO(eventId, memberId, null));
            }

            assertThat(response.event().status()).isEqualTo(EventStatus.SCHEDULED);
        }

        @Test
        @DisplayName("EP - already registered member in event -> conflict error")
        void alreadyRegisteredMemberInEventShouldReturnConflictError() {
            UUID memberId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            RegisterPresenceDTO dto = new RegisterPresenceDTO(eventId, memberId, null);
            EventEntity event = registrationEligibleEvent();

            when(getEventInstance.requiredByIdForUpdate(eventId)).thenReturn(event);
            when(eventSecurity.canGetEvent(event)).thenReturn(true);
            when(presenceRepo.existsByMember_IdAndEvent_Id(memberId, eventId)).thenReturn(true);

            assertThatThrownBy(() -> registerPresence.register(dto))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Presence already registered");

            verifyNoInteractions(getMemberInstance, presenceMapper);
            verify(presenceRepo, never()).save(any());
        }

        @Test
        @DisplayName("REQ-PRESENCE-005 - uniqueness safeguard wins registration race -> duplicate conflict identifies winning Presence")
        void uniquenessSafeguardLossShouldReturnDetailedPresenceConflict() {
            UUID memberId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            UUID winningPresenceId = UUID.randomUUID();
            RegisterPresenceDTO dto = new RegisterPresenceDTO(eventId, memberId, null);
            EventEntity event = registrationEligibleEvent();
            MemberEntity member = new MemberEntity();
            ConstraintViolationException constraintViolation = new ConstraintViolationException(
                    "duplicate active Event-Member Presence",
                    null,
                    "idx_presence_not_deleted"
            );
            DataIntegrityViolationException uniquenessLoss = new DataIntegrityViolationException(
                    "active Presence uniqueness violation",
                    constraintViolation
            );

            when(getEventInstance.requiredByIdForUpdate(eventId)).thenReturn(event);
            when(eventSecurity.canGetEvent(event)).thenReturn(true);
            when(presenceRepo.existsByMember_IdAndEvent_Id(memberId, eventId)).thenReturn(false);
            when(getMemberInstance.requiredById(memberId)).thenReturn(member);
            when(presenceRepo.save(anyPresenceEntity())).thenThrow(uniquenessLoss);
            when(conflictResolver.findWinningPresenceId(memberId, eventId))
                    .thenReturn(Optional.of(winningPresenceId));

            assertThatThrownBy(() -> registerPresence.register(dto))
                    .isInstanceOfSatisfying(ConflictException.class, conflict -> {
                        assertThat(conflict.getCode()).isEqualTo("PRESENCE_ALREADY_REGISTERED");
                        assertThat(conflict.getDetails())
                                .containsEntry("eventId", eventId)
                                .containsEntry("memberId", memberId)
                                .containsEntry("presenceId", winningPresenceId);
                    });

            verifyNoInteractions(presenceMapper, activityEvents);
            verify(conflictResolver).findWinningPresenceId(memberId, eventId);
        }

        @Test
        @DisplayName("EP - missing member id -> not found error")
        void missingMemberIdShouldReturnNotFoundError() {
            UUID memberId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            RegisterPresenceDTO dto = new RegisterPresenceDTO(eventId, memberId, null);
            EventEntity event = registrationEligibleEvent();

            when(getEventInstance.requiredByIdForUpdate(eventId)).thenReturn(event);
            when(eventSecurity.canGetEvent(event)).thenReturn(true);
            when(presenceRepo.existsByMember_IdAndEvent_Id(memberId, eventId)).thenReturn(false);
            when(getMemberInstance.requiredById(memberId))
                    .thenThrow(NotFoundException.resource("Member", memberId));

            assertThatThrownBy(() -> registerPresence.register(dto))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Member not found with identifier " + memberId);

            verifyNoInteractions(presenceMapper);
            verify(presenceRepo, never()).save(any());
        }

        @Test
        @DisplayName("EP - missing event id -> not found error")
        void missingEventIdShouldReturnNotFoundError() {
            UUID memberId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            RegisterPresenceDTO dto = new RegisterPresenceDTO(eventId, memberId, null);
            when(getEventInstance.requiredByIdForUpdate(eventId))
                    .thenThrow(NotFoundException.resource("Event", eventId));

            assertThatThrownBy(() -> registerPresence.register(dto))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Event not found with identifier " + eventId);

            verifyNoInteractions(eventSecurity, getMemberInstance, presenceMapper);
            verify(presenceRepo, never()).save(any());
        }
    }

    private static PresenceEntity anyPresenceEntity() {
        return org.mockito.ArgumentMatchers.any(PresenceEntity.class);
    }

    private static EventEntity registrationEligibleEvent() {
        EventEntity event = new EventEntity();
        event.setBeginDate(Instant.now().minusSeconds(3600));
        event.setEndDate(Instant.now().plusSeconds(3600));
        event.setStatus(EventStatus.SCHEDULED);
        return event;
    }
}
