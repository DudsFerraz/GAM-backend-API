package br.org.gam.api.shared.phonenumber;

import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@UnitTest
@DisplayName("GamPhoneNumber Spring Converter")
class GamPhoneNumberConverterTest {

    private final GamPhoneNumberConverter converter = new GamPhoneNumberConverter();

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - valid source -> canonical phone number")
        void validSourceShouldReturnPhoneNumber() {
            GamPhoneNumber phoneNumber = converter.convert("+5519998877665");

            Assertions.assertNotNull(phoneNumber);
            assertThat(phoneNumber.value()).isEqualTo("+5519998877665");
        }

        @Test
        @DisplayName("EP - malformed source -> conversion error")
        void malformedSourceShouldReturnConversionError() {
            assertThatThrownBy(() -> converter.convert("abc"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   ", "\t"})
        @DisplayName("EP - absent source -> conversion error")
        void absentSourceShouldReturnConversionError(String source) {
            assertThatThrownBy(() -> converter.convert(source))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @StructuralTest
    @DisplayName("Structural")
    class Structural {

        @Test
        @DisplayName("COND - converter -> Spring component")
        void converterShouldBeSpringComponent() {
            assertThat(GamPhoneNumberConverter.class).hasAnnotation(Component.class);
        }
    }
}
