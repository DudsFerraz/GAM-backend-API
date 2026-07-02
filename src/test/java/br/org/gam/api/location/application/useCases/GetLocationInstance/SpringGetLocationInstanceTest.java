package br.org.gam.api.location.application.useCases.GetLocationInstance;

import br.org.gam.api.location.application.LocationNotFoundException;
import br.org.gam.api.location.persistence.LocationEntity;
import br.org.gam.api.location.persistence.LocationRepository;
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
@DisplayName("Get Location Instance Use Case")
class SpringGetLocationInstanceTest {

    @Mock
    private LocationRepository locationRepo;

    @InjectMocks
    private SpringGetLocationInstance getLocationInstance;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - existing id -> location entity")
        void existingIdShouldReturnLocationEntity() {
            UUID id = UUID.randomUUID();
            LocationEntity entity = new LocationEntity();

            when(locationRepo.findById(id)).thenReturn(Optional.of(entity));

            LocationEntity result = getLocationInstance.entityById(id);

            assertThat(result).isSameAs(entity);
        }

        @Test
        @DisplayName("EP - missing id -> not found error")
        void missingIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(locationRepo.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getLocationInstance.entityById(id))
                    .isInstanceOf(LocationNotFoundException.class)
                    .hasMessage("Could not find location with id " + id);

        }
    }
}
