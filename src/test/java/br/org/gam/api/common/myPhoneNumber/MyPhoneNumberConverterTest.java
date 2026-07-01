package br.org.gam.api.common.myPhoneNumber;

import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@DisplayName("Phone Number Web Converter")
class MyPhoneNumberConverterTest {

    private final MyPhoneNumberConverter converter = new MyPhoneNumberConverter();

    @Nested
    @StructuralTest
    @DisplayName("Structural")
    class Structural {

        @Test
        @DisplayName("valid source -> phone number value object")
        void validSourceShouldReturnPhoneNumberValueObject() {
            MyPhoneNumber phoneNumber = converter.convert("+5519998877665");

            assertThat(phoneNumber).isNotNull();
            assertThat(phoneNumber.e164format()).isEqualTo("+5519998877665");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   ", "\t"})
        @DisplayName("blank source -> null")
        void blankSourceShouldReturnNull(String source) {
            assertThat(converter.convert(source)).isNull();
        }
    }
}
