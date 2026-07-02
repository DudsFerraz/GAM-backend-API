package br.org.gam.api.event.application.useCases;

import br.org.gam.api.event.application.EventMapper;
import br.org.gam.api.event.application.EventNotFoundException;
import br.org.gam.api.event.application.EventRDTO;
import br.org.gam.api.event.application.EventSecurity;
import br.org.gam.api.event.application.EventEntityLoader;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.time.Instant;
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
@DisplayName("Get Event Use Case")
class GetEventTest {

    @Mock
    private EventEntityLoader getEventInstance;

    @Mock
    private EventMapper eventMapper;

    @Mock
    private EventSecurity eventSecurity;

    @InjectMocks
    private GetEvent getEvent;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - visible event id -> event response")
        void visibleEventIdShouldReturnEventResponse() {
            UUID id = UUID.randomUUID();
            EventEntity entity = new EventEntity();
            EventRDTO expectedResponse = response(id);

            when(getEventInstance.requiredById(id)).thenReturn(entity);
            when(eventSecurity.canGetEvent(entity)).thenReturn(true);
            when(eventMapper.entityToRDTO(entity)).thenReturn(expectedResponse);

            EventRDTO response = getEvent.byId(id);

            assertThat(response).isSameAs(expectedResponse);
            verify(getEventInstance).requiredById(id);
            verify(eventSecurity).canGetEvent(entity);
            verify(eventMapper).entityToRDTO(entity);
        }

        @Test
        @DisplayName("EP - hidden event id -> not found error")
        void hiddenEventIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();
            EventEntity entity = new EventEntity();

            when(getEventInstance.requiredById(id)).thenReturn(entity);
            when(eventSecurity.canGetEvent(entity)).thenReturn(false);

            assertThatThrownBy(() -> getEvent.byId(id))
                    .isInstanceOf(EventNotFoundException.class)
                    .hasMessage("Could not find event with id " + id);

            verify(eventSecurity).canGetEvent(entity);
            verifyNoInteractions(eventMapper);
        }

        @Test
        @DisplayName("EP - missing event id -> not found error")
        void missingEventIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(getEventInstance.requiredById(id))
                    .thenThrow(new EventNotFoundException("Could not find event with id " + id));

            assertThatThrownBy(() -> getEvent.byId(id))
                    .isInstanceOf(EventNotFoundException.class)
                    .hasMessage("Could not find event with id " + id);

            verifyNoInteractions(eventSecurity, eventMapper);
        }
    }

    private static EventRDTO response(UUID id) {
        return new EventRDTO(
                id,
                "Event",
                "",
                null,
                null,
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200),
                EventType.MISSA,
                EventStatus.SCHEDULED
        );
    }
}
