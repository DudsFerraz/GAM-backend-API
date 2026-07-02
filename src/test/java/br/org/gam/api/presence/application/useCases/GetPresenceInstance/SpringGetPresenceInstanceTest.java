package br.org.gam.api.presence.application.useCases.GetPresenceInstance;

import br.org.gam.api.presence.application.PresenceNotFoundException;
import br.org.gam.api.presence.persistence.PresenceEntity;
import br.org.gam.api.presence.persistence.PresenceRepository;
import br.org.gam.api.testing.annotation.FunctionalTest;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Get Presence Instance Use Case")
class SpringGetPresenceInstanceTest {

    @Mock
    private PresenceRepository presenceRepo;

    @InjectMocks
    private SpringGetPresenceInstance getPresenceInstance;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - existing id -> presence entity")
        void existingIdShouldReturnPresenceEntity() {
            UUID id = UUID.randomUUID();
            PresenceEntity entity = new PresenceEntity();

            when(presenceRepo.findById(id)).thenReturn(Optional.of(entity));

            PresenceEntity result = getPresenceInstance.entityById(id);

            assertThat(result).isSameAs(entity);
        }

        @Test
        @DisplayName("EP - missing id -> not found error")
        void missingIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(presenceRepo.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getPresenceInstance.entityById(id))
                    .isInstanceOf(PresenceNotFoundException.class)
                    .hasMessage("Could not find presence with id " + id);

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

        }
    }
}
