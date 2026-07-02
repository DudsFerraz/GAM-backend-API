package br.org.gam.api.event.application.useCases.GetEventInstance;

import br.org.gam.api.event.application.EventMapper;
import br.org.gam.api.event.application.EventNotFoundException;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.event.persistence.EventRepository;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.time.Instant;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Get Event Instance Use Case")
class SpringGetEventInstanceTest {

    @Mock
    private EventRepository eventRepo;

    @Mock
    private EventMapper eventMapper;

    @InjectMocks
    private SpringGetEventInstance getEventInstance;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - existing id -> domain event")
        void existingIdShouldReturnDomainEvent() {
            UUID id = UUID.randomUUID();
            EventEntity entity = new EventEntity();
            Event domain = event();

            when(eventRepo.findById(id)).thenReturn(Optional.of(entity));
            when(eventMapper.entityToDomain(entity)).thenReturn(domain);

            Event result = getEventInstance.domainById(id);

            assertThat(result).isSameAs(domain);
            verify(eventMapper).entityToDomain(entity);
        }

        @Test
        @DisplayName("EP - existing id -> event entity")
        void existingIdShouldReturnEventEntity() {
            UUID id = UUID.randomUUID();
            EventEntity entity = new EventEntity();

            when(eventRepo.findById(id)).thenReturn(Optional.of(entity));

            EventEntity result = getEventInstance.entityById(id);

            assertThat(result).isSameAs(entity);
            verifyNoInteractions(eventMapper);
        }

        @Test
        @DisplayName("EP - missing id -> not found error")
        void missingIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(eventRepo.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getEventInstance.entityById(id))
                    .isInstanceOf(EventNotFoundException.class)
                    .hasMessage("Could not find event with id " + id);

            verifyNoInteractions(eventMapper);
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

            when(eventRepo.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getEventInstance.domainById(id))
                    .isInstanceOf(EventNotFoundException.class)
                    .hasMessage("Could not find event with id " + id);

            verifyNoInteractions(eventMapper);
        }
    }

    private static Event event() {
        Instant beginDate = Instant.now().plusSeconds(3600);
        return Event.register("Event", null, beginDate, beginDate.plusSeconds(3600), EventType.MISSA);
    }
}
