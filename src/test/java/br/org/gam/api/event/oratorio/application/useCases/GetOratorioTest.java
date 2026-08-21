package br.org.gam.api.event.oratorio.application.useCases;

import br.org.gam.api.event.application.EventSecurity;
import br.org.gam.api.event.application.EventEntityLoader;
import br.org.gam.api.event.oratorio.application.OratorioMapper;
import br.org.gam.api.event.oratorio.application.OratorioRDTO;
import br.org.gam.api.event.oratorio.application.OratorioEntityLoader;
import br.org.gam.api.event.oratorio.persistence.OratorioEntity;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.shared.exception.NotFoundException;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.StructuralTest;
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
import org.springframework.stereotype.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Get Oratorio Use Case")
class GetOratorioTest {

    @Mock
    private OratorioMapper oratorioMapper;

    @Mock
    private EventEntityLoader getEventInstance;

    @Mock
    private EventSecurity eventSecurity;

    @Mock
    private OratorioEntityLoader getOratorioInstance;

    @InjectMocks
    private GetOratorio getOratorio;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - visible oratorio id -> oratorio response")
        void visibleOratorioIdShouldReturnOratorioResponse() {
            UUID id = UUID.randomUUID();
            EventEntity eventEntity = new EventEntity();
            OratorioEntity oratorioEntity = new OratorioEntity();
            OratorioRDTO expectedResponse = new OratorioRDTO(id, null, null, Set.of(), Set.of(), Set.of(), Set.of());

            when(getEventInstance.requiredById(id)).thenReturn(eventEntity);
            when(eventSecurity.canGetEvent(eventEntity)).thenReturn(true);
            when(getOratorioInstance.requiredById(id)).thenReturn(oratorioEntity);
            when(oratorioMapper.entityToRDTO(oratorioEntity)).thenReturn(expectedResponse);

            OratorioRDTO response = getOratorio.byId(id);

            assertThat(response).isSameAs(expectedResponse);
            verify(getOratorioInstance).requiredById(id);
            verify(oratorioMapper).entityToRDTO(oratorioEntity);
        }

        @Test
        @DisplayName("EP - hidden oratorio id -> not found error")
        void hiddenOratorioIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();
            EventEntity eventEntity = new EventEntity();

            when(getEventInstance.requiredById(id)).thenReturn(eventEntity);
            when(eventSecurity.canGetEvent(eventEntity)).thenReturn(false);

            assertThatThrownBy(() -> getOratorio.byId(id))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Oratorio not found with identifier " + id);

            verifyNoInteractions(getOratorioInstance, oratorioMapper);
        }

        @Test
        @DisplayName("EP - missing event id -> not found error")
        void missingEventIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(getEventInstance.requiredById(id))
                    .thenThrow(NotFoundException.resource("Event", id));

            assertThatThrownBy(() -> getOratorio.byId(id))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Event not found with identifier " + id);

            verifyNoInteractions(eventSecurity, getOratorioInstance, oratorioMapper);
        }

        @Test
        @DisplayName("EP - visible event without oratorio -> not found error")
        void visibleEventWithoutOratorioShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();
            EventEntity eventEntity = new EventEntity();

            when(getEventInstance.requiredById(id)).thenReturn(eventEntity);
            when(eventSecurity.canGetEvent(eventEntity)).thenReturn(true);
            when(getOratorioInstance.requiredById(id))
                    .thenThrow(NotFoundException.resource("Oratorio", id));

            assertThatThrownBy(() -> getOratorio.byId(id))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Oratorio not found with identifier " + id);

            verify(eventSecurity).canGetEvent(eventEntity);
            verifyNoInteractions(oratorioMapper);
        }
    }

    @Nested
    @StructuralTest
    @DisplayName("Structural")
    class Structural {

        @Test
        @DisplayName("Spring use case implementation -> service component")
        void springUseCaseImplementationShouldBeServiceComponent() {
            assertThat(GetOratorio.class).hasAnnotation(Service.class);
        }
    }
}
