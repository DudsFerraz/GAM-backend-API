package br.org.gam.api.Entities.events.oratorio.services;

import br.org.gam.api.Entities.events.generic.Event;
import br.org.gam.api.Entities.events.generic.EventType;
import br.org.gam.api.Entities.events.oratorio.Oratorio;
import br.org.gam.api.Entities.events.oratorio.OratorioMapper;
import br.org.gam.api.Entities.events.oratorio.exception.OratorioNotFoundException;
import br.org.gam.api.Entities.events.oratorio.persistence.OratorioEntity;
import br.org.gam.api.Entities.events.oratorio.persistence.OratorioRepository;
import br.org.gam.api.Entities.events.oratorio.services.getOratorioInstance.SpringGetOratorioInstance;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Get Oratorio Instance Use Case")
class SpringGetOratorioInstanceTest {

    @Mock
    private OratorioRepository oratorioRepo;

    @Mock
    private OratorioMapper oratorioMapper;

    @InjectMocks
    private SpringGetOratorioInstance getOratorioInstance;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - existing id -> domain oratorio")
        void existingIdShouldReturnDomainOratorio() {
            UUID id = UUID.randomUUID();
            OratorioEntity entity = new OratorioEntity();
            Oratorio domain = oratorio();

            when(oratorioRepo.findById(id)).thenReturn(Optional.of(entity));
            when(oratorioMapper.entityToDomain(entity)).thenReturn(domain);

            Oratorio result = getOratorioInstance.domainById(id);

            assertThat(result).isSameAs(domain);
            verify(oratorioMapper).entityToDomain(entity);
        }

        @Test
        @DisplayName("EP - existing id -> oratorio entity")
        void existingIdShouldReturnOratorioEntity() {
            UUID id = UUID.randomUUID();
            OratorioEntity entity = new OratorioEntity();

            when(oratorioRepo.findById(id)).thenReturn(Optional.of(entity));

            OratorioEntity result = getOratorioInstance.entityById(id);

            assertThat(result).isSameAs(entity);
            verifyNoInteractions(oratorioMapper);
        }

        @Test
        @DisplayName("EP - missing id -> not found error")
        void missingIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(oratorioRepo.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getOratorioInstance.entityById(id))
                    .isInstanceOf(OratorioNotFoundException.class)
                    .hasMessage("Could not find oratorio with id " + id);

            verifyNoInteractions(oratorioMapper);
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

            when(oratorioRepo.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getOratorioInstance.domainById(id))
                    .isInstanceOf(OratorioNotFoundException.class)
                    .hasMessage("Could not find oratorio with id " + id);

            verifyNoInteractions(oratorioMapper);
        }
    }

    private static Oratorio oratorio() {
        Instant beginDate = Instant.now().plusSeconds(3600);
        Event event = Event.register("Oratorio", null, null, null, beginDate, beginDate.plusSeconds(3600), EventType.ORATORIO);
        return Oratorio.register(event, null, null, null, null);
    }
}
