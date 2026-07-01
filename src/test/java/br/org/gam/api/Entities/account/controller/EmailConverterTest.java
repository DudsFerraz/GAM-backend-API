package br.org.gam.api.Entities.account.controller;

import br.org.gam.api.Entities.account.myEmail.MyEmail;
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
@DisplayName("Email Web Converter")
class EmailConverterTest {

    private final EmailConverter converter = new EmailConverter();

    @Nested
    @StructuralTest
    @DisplayName("Structural")
    class Structural {

        @Test
        @DisplayName("valid source -> email value object")
        void validSourceShouldReturnEmailValueObject() {
            MyEmail email = converter.convert("user@example.com");

            assertThat(email).isNotNull();
            assertThat(email.value()).isEqualTo("user@example.com");
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
