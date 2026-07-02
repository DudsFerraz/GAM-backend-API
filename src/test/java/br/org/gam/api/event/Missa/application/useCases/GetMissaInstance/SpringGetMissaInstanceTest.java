package br.org.gam.api.event.Missa.application.useCases.GetMissaInstance;

import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.event.Missa.application.MissaMapper;
import br.org.gam.api.event.Missa.application.MissaNotFoundException;
import br.org.gam.api.event.Missa.domain.Missa;
import br.org.gam.api.event.Missa.persistence.MissaEntity;
import br.org.gam.api.event.Missa.persistence.MissaRepository;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.time.Instant;
import java.util.Optional;
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
@DisplayName("Get Missa Instance Use Case")
class SpringGetMissaInstanceTest {

    @Mock
    private MissaRepository missaRepo;

    @Mock
    private MissaMapper missaMapper;

    @InjectMocks
    private SpringGetMissaInstance getMissaInstance;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - existing id -> domain missa")
        void existingIdShouldReturnDomainMissa() {
            UUID id = UUID.randomUUID();
            MissaEntity entity = new MissaEntity();
            Missa domain = missa();

            when(missaRepo.findById(id)).thenReturn(Optional.of(entity));
            when(missaMapper.entityToDomain(entity)).thenReturn(domain);

            Missa result = getMissaInstance.domainById(id);

            assertThat(result).isSameAs(domain);
            verify(missaMapper).entityToDomain(entity);
        }

        @Test
        @DisplayName("EP - existing id -> missa entity")
        void existingIdShouldReturnMissaEntity() {
            UUID id = UUID.randomUUID();
            MissaEntity entity = new MissaEntity();

            when(missaRepo.findById(id)).thenReturn(Optional.of(entity));

            MissaEntity result = getMissaInstance.entityById(id);

            assertThat(result).isSameAs(entity);
            verifyNoInteractions(missaMapper);
        }

        @Test
        @DisplayName("EP - missing id -> not found error")
        void missingIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(missaRepo.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getMissaInstance.entityById(id))
                    .isInstanceOf(MissaNotFoundException.class)
                    .hasMessage("Could not find missa with id " + id);

            verifyNoInteractions(missaMapper);
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

            when(missaRepo.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getMissaInstance.domainById(id))
                    .isInstanceOf(MissaNotFoundException.class)
                    .hasMessage("Could not find missa with id " + id);

            verifyNoInteractions(missaMapper);
        }
    }

    private static Missa missa() {
        Instant beginDate = Instant.now().plusSeconds(3600);
        Event event = Event.register("Missa", null, null, null, beginDate, beginDate.plusSeconds(3600), EventType.MISSA);
        return Missa.register(event, null, null, null, null, null, Set.of());
    }
}
