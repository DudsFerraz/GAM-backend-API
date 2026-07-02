package br.org.gam.api.location.domain;

import br.org.gam.api.shared.domain.Name;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@UnitTest
@DisplayName("Location Aggregate")
class LocationTest {

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - valid location data -> location with generated identity")
        void validLocationDataShouldCreateLocationWithGeneratedIdentity() {
            BigDecimal latitude = new BigDecimal("-22.90684670");
            BigDecimal longitude = new BigDecimal("-47.06158810");

            Location location = Location.register(
                    "  Parish Hall  ",
                    "  Rua das Flores, 100  ",
                    "  Campinas  ",
                    "  SP  ",
                    "  13000-000  ",
                    "  BRA  ",
                    latitude,
                    longitude
            );

            assertThat(location.getId()).isNotNull();
            assertThat(location.getId().version()).isEqualTo(7);
            assertThat(location.getName()).isEqualTo("Parish Hall");
            assertThat(location.getStreet()).isEqualTo("Rua das Flores, 100");
            assertThat(location.getCity()).isEqualTo("Campinas");
            assertThat(location.getState()).isEqualTo("SP");
            assertThat(location.getPostalCode()).isEqualTo("13000-000");
            assertThat(location.getCountryCode()).isEqualTo("BRA");
            assertThat(location.getLatitude()).isSameAs(latitude);
            assertThat(location.getLongitude()).isSameAs(longitude);
        }

        @Test
        @DisplayName("EP - null optional data -> empty optional text and null coordinates")
        void nullOptionalDataShouldCreateLocationWithEmptyOptionalTextAndNullCoordinates() {
            Location location = Location.register("Parish Hall", null, "Campinas", "SP", null, "BRA", null, null);

            assertThat(location.getStreet()).isEmpty();
            assertThat(location.getPostalCode()).isEmpty();
            assertThat(location.getLatitude()).isNull();
            assertThat(location.getLongitude()).isNull();
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null name -> validation error")
        void nullNameShouldReturnValidationError(String name) {
            assertThatNullPointerException()
                    .isThrownBy(() -> Location.register(name, null, "Campinas", "SP", null, "BRA", null, null))
                    .withMessage("Name cannot be null");
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null city -> validation error")
        void nullCityShouldReturnValidationError(String city) {
            assertThatNullPointerException()
                    .isThrownBy(() -> Location.register("Parish Hall", null, city, "SP", null, "BRA", null, null))
                    .withMessage("City cannot be null");
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null state -> validation error")
        void nullStateShouldReturnValidationError(String state) {
            assertThatNullPointerException()
                    .isThrownBy(() -> Location.register("Parish Hall", null, "Campinas", state, null, "BRA", null, null))
                    .withMessage("State cannot be null");
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null country code -> validation error")
        void nullCountryCodeShouldReturnValidationError(String countryCode) {
            assertThatNullPointerException()
                    .isThrownBy(() -> Location.register("Parish Hall", null, "Campinas", "SP", null, countryCode, null, null))
                    .withMessage("CountryCode cannot be null");
        }
    }
}
