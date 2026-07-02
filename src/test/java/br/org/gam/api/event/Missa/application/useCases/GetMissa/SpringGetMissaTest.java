package br.org.gam.api.event.Missa.application.useCases.GetMissa;

import br.org.gam.api.event.application.EventNotFoundException;
import br.org.gam.api.event.application.EventSecurity;
import br.org.gam.api.event.application.useCases.GetEventInstance.GetEventInstance;
import br.org.gam.api.event.Missa.application.MissaMapper;
import br.org.gam.api.event.Missa.application.MissaNotFoundException;
import br.org.gam.api.event.Missa.application.MissaRDTO;
import br.org.gam.api.event.Missa.application.useCases.GetMissaInstance.GetMissaInstance;
import br.org.gam.api.event.Missa.domain.Missa;
import br.org.gam.api.event.Missa.persistence.MissaEntity;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.Set;
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
@DisplayName("Get Missa Use Case")
class SpringGetMissaTest {

    @Mock
    private MissaMapper missaMapper;

    @Mock
    private GetMissaInstance getMissaInstance;

    @Mock
    private GetEventInstance getEventInstance;

    @Mock
    private EventSecurity eventSecurity;

    @InjectMocks
    private SpringGetMissa getMissa;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - visible missa id -> missa response")
        void visibleMissaIdShouldReturnMissaResponse() {
            UUID id = UUID.randomUUID();
            EventEntity eventEntity = new EventEntity();
            MissaEntity missaEntity = new MissaEntity();
            MissaRDTO expectedResponse = new MissaRDTO(id, null, null, null, null, null, null, Set.of());

            when(getEventInstance.entityById(id)).thenReturn(eventEntity);
            when(eventSecurity.canGetEvent(eventEntity)).thenReturn(true);
            when(getMissaInstance.entityById(id)).thenReturn(missaEntity);
            when(missaMapper.entityToRDTO(missaEntity)).thenReturn(expectedResponse);

            MissaRDTO response = getMissa.byId(id);

            assertThat(response).isSameAs(expectedResponse);
            verify(getMissaInstance).entityById(id);
            verify(missaMapper).entityToRDTO(missaEntity);
        }

        @Test
        @DisplayName("EP - hidden missa id -> not found error")
        void hiddenMissaIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();
            EventEntity eventEntity = new EventEntity();

            when(getEventInstance.entityById(id)).thenReturn(eventEntity);
            when(eventSecurity.canGetEvent(eventEntity)).thenReturn(false);

            assertThatThrownBy(() -> getMissa.byId(id))
                    .isInstanceOf(EventNotFoundException.class)
                    .hasMessage("Could not find missa with id " + id);

            verifyNoInteractions(getMissaInstance, missaMapper);
        }

        @Test
        @DisplayName("EP - missing event id -> not found error")
        void missingEventIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(getEventInstance.entityById(id))
                    .thenThrow(new EventNotFoundException("Could not find event with id " + id));

            assertThatThrownBy(() -> getMissa.byId(id))
                    .isInstanceOf(EventNotFoundException.class)
                    .hasMessage("Could not find event with id " + id);

            verifyNoInteractions(eventSecurity, getMissaInstance, missaMapper);
        }

        @Test
        @DisplayName("EP - visible event without missa -> not found error")
        void visibleEventWithoutMissaShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();
            EventEntity eventEntity = new EventEntity();

            when(getEventInstance.entityById(id)).thenReturn(eventEntity);
            when(eventSecurity.canGetEvent(eventEntity)).thenReturn(true);
            when(getMissaInstance.entityById(id))
                    .thenThrow(new MissaNotFoundException("Could not find missa with id " + id));

            assertThatThrownBy(() -> getMissa.byId(id))
                    .isInstanceOf(MissaNotFoundException.class)
                    .hasMessage("Could not find missa with id " + id);

            verify(eventSecurity).canGetEvent(eventEntity);
            verifyNoInteractions(missaMapper);
        }
    }
}
