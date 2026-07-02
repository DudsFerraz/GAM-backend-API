package br.org.gam.api.account.domain;

import br.org.gam.api.account.persistence.EmailConverterJPA;
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
@DisplayName("Email JPA Converter")
class EmailConverterJPATest {

    private final EmailConverterJPA converter = new EmailConverterJPA();

    @Nested
    @StructuralTest
    @DisplayName("Structural")
    class Structural {

        @Test
        @DisplayName("email value object -> database value")
        void emailValueObjectShouldReturnDatabaseValue() {
            assertThat(converter.convertToDatabaseColumn(MyEmail.of("user@example.com"))).isEqualTo("user@example.com");
        }

        @Test
        @DisplayName("database value -> email value object")
        void databaseValueShouldReturnEmailValueObject() {
            MyEmail email = converter.convertToEntityAttribute("user@example.com");

            assertThat(email.value()).isEqualTo("user@example.com");
        }

        @Test
        @DisplayName("null email value object -> null database value")
        void nullEmailValueObjectShouldReturnNullDatabaseValue() {
            assertThat(converter.convertToDatabaseColumn(null)).isNull();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   ", "\t"})
        @DisplayName("blank database value -> null entity attribute")
        void blankDatabaseValueShouldReturnNullEntityAttribute(String dbData) {
            assertThat(converter.convertToEntityAttribute(dbData)).isNull();
        }
    }
}
