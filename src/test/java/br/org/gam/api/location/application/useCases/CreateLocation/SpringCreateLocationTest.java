package br.org.gam.api.location.application.useCases.CreateLocation;

import br.org.gam.api.location.application.LocationMapper;
import br.org.gam.api.location.domain.Location;
import br.org.gam.api.location.persistence.LocationEntity;
import br.org.gam.api.location.persistence.LocationRepository;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Create Location Use Case")
class SpringCreateLocationTest {

    @Mock
    private LocationRepository locationRepo;

    @Mock
    private LocationMapper locationMapper;

    @InjectMocks
    private SpringCreateLocation createLocation;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - valid location data -> location is created")
        void validLocationDataShouldCreateLocation() {
            BigDecimal latitude = new BigDecimal("-22.90684670");
            BigDecimal longitude = new BigDecimal("-47.06158810");
            CreateLocationDTO dto = new CreateLocationDTO(
                    "  Parish Hall  ",
                    null,
                    "  Campinas  ",
                    "  SP  ",
                    null,
                    "  BRA  ",
                    latitude,
                    longitude
            );
            LocationEntity mappedEntity = new LocationEntity();
            LocationEntity savedEntity = new LocationEntity();
            CreateLocationRDTO expectedResponse = new CreateLocationRDTO(UUID.randomUUID());

            when(locationMapper.domainToEntity(any(Location.class))).thenReturn(mappedEntity);
            when(locationRepo.save(mappedEntity)).thenReturn(savedEntity);
            when(locationMapper.entityToCreateLocationRDTO(savedEntity)).thenReturn(expectedResponse);

            CreateLocationRDTO response = createLocation.create(dto);

            assertThat(response).isSameAs(expectedResponse);

            ArgumentCaptor<Location> locationCaptor = ArgumentCaptor.forClass(Location.class);
            verify(locationMapper).domainToEntity(locationCaptor.capture());
            Location location = locationCaptor.getValue();

            assertThat(location.getId()).isNotNull();
            assertThat(location.getId().version()).isEqualTo(7);
            assertThat(location.getName()).isEqualTo("Parish Hall");
            assertThat(location.getStreet()).isEmpty();
            assertThat(location.getCity()).isEqualTo("Campinas");
            assertThat(location.getState()).isEqualTo("SP");
            assertThat(location.getPostalCode()).isEmpty();
            assertThat(location.getCountryCode()).isEqualTo("BRA");
            assertThat(location.getLatitude()).isSameAs(latitude);
            assertThat(location.getLongitude()).isSameAs(longitude);
            verify(locationRepo).save(mappedEntity);
        }
    }
}
