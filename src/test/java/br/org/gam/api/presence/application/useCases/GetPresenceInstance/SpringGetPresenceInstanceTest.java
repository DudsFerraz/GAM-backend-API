package br.org.gam.api.presence.application.useCases.GetPresenceInstance;

import br.org.gam.api.event.domain.Event;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.presence.application.PresenceMapper;
import br.org.gam.api.presence.application.PresenceNotFoundException;
import br.org.gam.api.presence.domain.Presence;
import br.org.gam.api.presence.persistence.PresenceEntity;
import br.org.gam.api.presence.persistence.PresenceRepository;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Get Presence Instance Use Case")
class SpringGetPresenceInstanceTest {

    @Mock
    private PresenceRepository presenceRepo;

    @Mock
    private PresenceMapper presenceMapper;

    @InjectMocks
    private SpringGetPresenceInstance getPresenceInstance;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - existing id -> domain presence")
        void existingIdShouldReturnDomainPresence() {
            UUID id = UUID.randomUUID();
            PresenceEntity entity = new PresenceEntity();
            Presence domain = presence();

            when(presenceRepo.findById(id)).thenReturn(Optional.of(entity));
            when(presenceMapper.entityToDomain(entity)).thenReturn(domain);

            Presence result = getPresenceInstance.domainById(id);

            assertThat(result).isSameAs(domain);
            verify(presenceMapper).entityToDomain(entity);
        }

        @Test
        @DisplayName("EP - existing id -> presence entity")
        void existingIdShouldReturnPresenceEntity() {
            UUID id = UUID.randomUUID();
            PresenceEntity entity = new PresenceEntity();

            when(presenceRepo.findById(id)).thenReturn(Optional.of(entity));

            PresenceEntity result = getPresenceInstance.entityById(id);

            assertThat(result).isSameAs(entity);
            verifyNoInteractions(presenceMapper);
        }

        @Test
        @DisplayName("EP - missing id -> not found error")
        void missingIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(presenceRepo.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getPresenceInstance.entityById(id))
                    .isInstanceOf(PresenceNotFoundException.class)
                    .hasMessage("Could not find presence with id " + id);

            verifyNoInteractions(presenceMapper);
        }

        @Test
        @DisplayName("EP - existing member and event ids -> domain presence")
        void existingMemberAndEventIdsShouldReturnDomainPresence() {
            UUID memberId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            PresenceEntity entity = new PresenceEntity();
            Presence domain = presence();

            when(presenceRepo.findByMember_IdAndEvent_Id(memberId, eventId)).thenReturn(Optional.of(entity));
            when(presenceMapper.entityToDomain(entity)).thenReturn(domain);

            Presence result = getPresenceInstance.domainByIds(memberId, eventId);

            assertThat(result).isSameAs(domain);
            verify(presenceMapper).entityToDomain(entity);
        }

        @Test
        @DisplayName("EP - existing member and event ids -> presence entity")
        void existingMemberAndEventIdsShouldReturnPresenceEntity() {
            UUID memberId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            PresenceEntity entity = new PresenceEntity();

            when(presenceRepo.findByMember_IdAndEvent_Id(memberId, eventId)).thenReturn(Optional.of(entity));

            PresenceEntity result = getPresenceInstance.entityByIds(memberId, eventId);

            assertThat(result).isSameAs(entity);
            verifyNoInteractions(presenceMapper);
        }

        @Test
        @DisplayName("EP - missing member and event ids -> not found error")
        void missingMemberAndEventIdsShouldReturnNotFoundError() {
            UUID memberId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();

            when(presenceRepo.findByMember_IdAndEvent_Id(memberId, eventId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getPresenceInstance.entityByIds(memberId, eventId))
                    .isInstanceOf(PresenceNotFoundException.class)
                    .hasMessage("member with id: " + memberId + " has no presence registered in event with id: " + eventId);

            verifyNoInteractions(presenceMapper);
        }
    }

    @Nested
    @StructuralTest
    @DisplayName("Structural")
    class Structural {

        @Test
        @DisplayName("missing id for domain lookup -> not found error")
        void missingIdForDomainLookupShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(presenceRepo.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getPresenceInstance.domainById(id))
                    .isInstanceOf(PresenceNotFoundException.class)
                    .hasMessage("Could not find presence with id " + id);

            verifyNoInteractions(presenceMapper);
        }

        @Test
        @DisplayName("missing member and event ids for domain lookup -> not found error")
        void missingMemberAndEventIdsForDomainLookupShouldReturnNotFoundError() {
            UUID memberId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();

            when(presenceRepo.findByMember_IdAndEvent_Id(memberId, eventId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getPresenceInstance.domainByIds(memberId, eventId))
                    .isInstanceOf(PresenceNotFoundException.class)
                    .hasMessage("member with id: " + memberId + " has no presence registered in event with id: " + eventId);

            verifyNoInteractions(presenceMapper);
        }
    }

    private static Presence presence() {
        return Presence.register(mock(Member.class), mock(Event.class), null);
    }
}
