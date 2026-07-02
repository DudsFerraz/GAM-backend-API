package br.org.gam.api.location.application.useCases;

import br.org.gam.api.location.application.LocationMapper;
import br.org.gam.api.location.application.LocationNotFoundException;
import br.org.gam.api.location.application.LocationRDTO;
import br.org.gam.api.location.application.LocationEntityLoader;
import br.org.gam.api.location.persistence.LocationEntity;
import br.org.gam.api.location.persistence.LocationRepository;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Get Location Use Case")
class GetLocationTest {

    @Mock
    private LocationEntityLoader getLocationInstance;

    @Mock
    private LocationMapper locationMapper;

    @Mock
    private LocationRepository locationRepo;

    @InjectMocks
    private GetLocation getLocation;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - existing location id -> location response")
        void existingLocationIdShouldReturnLocationResponse() {
            UUID id = UUID.randomUUID();
            LocationEntity entity = new LocationEntity();
            LocationRDTO expectedResponse = response(id, "Parish Hall");

            when(getLocationInstance.requiredById(id)).thenReturn(entity);
            when(locationMapper.entityToLocationRDTO(entity)).thenReturn(expectedResponse);

            LocationRDTO response = getLocation.byId(id);

            assertThat(response).isSameAs(expectedResponse);
            verify(getLocationInstance).requiredById(id);
            verify(locationMapper).entityToLocationRDTO(entity);
        }

        @Test
        @DisplayName("EP - missing location id -> not found error")
        void missingLocationIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(getLocationInstance.requiredById(id))
                    .thenThrow(new LocationNotFoundException("Could not find location with id " + id));

            assertThatThrownBy(() -> getLocation.byId(id))
                    .isInstanceOf(LocationNotFoundException.class)
                    .hasMessage("Could not find location with id " + id);

            verifyNoInteractions(locationMapper, locationRepo);
        }

        @Test
        @DisplayName("EP - pageable locations -> mapped location page")
        void pageableLocationsShouldReturnMappedLocationPage() {
            Pageable pageable = PageRequest.of(0, 10);
            LocationEntity firstEntity = new LocationEntity();
            LocationEntity secondEntity = new LocationEntity();
            LocationRDTO firstResponse = response(UUID.randomUUID(), "Parish Hall");
            LocationRDTO secondResponse = response(UUID.randomUUID(), "Main Church");

            when(locationRepo.findAll(pageable))
                    .thenReturn(new PageImpl<>(List.of(firstEntity, secondEntity), pageable, 2));
            when(locationMapper.entityToLocationRDTO(firstEntity)).thenReturn(firstResponse);
            when(locationMapper.entityToLocationRDTO(secondEntity)).thenReturn(secondResponse);

            Page<LocationRDTO> response = getLocation.all(pageable);

            assertThat(response.getContent()).containsExactly(firstResponse, secondResponse);
            assertThat(response.getTotalElements()).isEqualTo(2);
            verify(locationRepo).findAll(pageable);
            verify(locationMapper).entityToLocationRDTO(firstEntity);
            verify(locationMapper).entityToLocationRDTO(secondEntity);
        }
    }

    @Nested
    @StructuralTest
    @DisplayName("Structural")
    class Structural {

        @Test
        @DisplayName("empty page -> empty mapped page")
        void emptyPageShouldReturnEmptyMappedPage() {
            Pageable pageable = PageRequest.of(0, 10);

            when(locationRepo.findAll(pageable)).thenReturn(Page.empty(pageable));

            Page<LocationRDTO> response = getLocation.all(pageable);

            assertThat(response.getContent()).isEmpty();
            assertThat(response.getTotalElements()).isZero();
            verify(locationRepo).findAll(pageable);
            verifyNoInteractions(locationMapper);
        }
    }

    private static LocationRDTO response(UUID id, String name) {
        return new LocationRDTO(
                id,
                name,
                "",
                "Campinas",
                "SP",
                "",
                "BRA",
                new BigDecimal("-22.90684670"),
                new BigDecimal("-47.06158810")
        );
    }
}
