package br.org.gam.api.location.application.useCases.GetLocationInstance;

import br.org.gam.api.location.application.LocationMapper;
import br.org.gam.api.location.application.LocationNotFoundException;
import br.org.gam.api.location.domain.Location;
import br.org.gam.api.location.persistence.LocationEntity;
import br.org.gam.api.location.persistence.LocationRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Get Location Instance Use Case")
class SpringGetLocationInstanceTest {

    @Mock
    private LocationRepository locationRepo;

    @Mock
    private LocationMapper locationMapper;

    @InjectMocks
    private SpringGetLocationInstance getLocationInstance;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - existing id -> domain location")
        void existingIdShouldReturnDomainLocation() {
            UUID id = UUID.randomUUID();
            LocationEntity entity = new LocationEntity();
            Location domain = location();

            when(locationRepo.findById(id)).thenReturn(Optional.of(entity));
            when(locationMapper.entityToDomain(entity)).thenReturn(domain);

            Location result = getLocationInstance.domainById(id);

            assertThat(result).isSameAs(domain);
            verify(locationMapper).entityToDomain(entity);
        }

        @Test
        @DisplayName("EP - existing id -> location entity")
        void existingIdShouldReturnLocationEntity() {
            UUID id = UUID.randomUUID();
            LocationEntity entity = new LocationEntity();

            when(locationRepo.findById(id)).thenReturn(Optional.of(entity));

            LocationEntity result = getLocationInstance.entityById(id);

            assertThat(result).isSameAs(entity);
            verifyNoInteractions(locationMapper);
        }

        @Test
        @DisplayName("EP - missing id -> not found error")
        void missingIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(locationRepo.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getLocationInstance.entityById(id))
                    .isInstanceOf(LocationNotFoundException.class)
                    .hasMessage("Could not find location with id " + id);

            verifyNoInteractions(locationMapper);
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

            when(locationRepo.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getLocationInstance.domainById(id))
                    .isInstanceOf(LocationNotFoundException.class)
                    .hasMessage("Could not find location with id " + id);

            verifyNoInteractions(locationMapper);
        }
    }

    private static Location location() {
        return Location.register("Parish Hall", null, "Campinas", "SP", null, "BRA", null, null);
    }
}
