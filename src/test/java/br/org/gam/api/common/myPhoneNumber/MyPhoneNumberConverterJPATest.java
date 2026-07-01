package br.org.gam.api.common.myPhoneNumber;

import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@DisplayName("Phone Number JPA Converter")
class MyPhoneNumberConverterJPATest {

    private final MyPhoneNumberConverterJPA converter = new MyPhoneNumberConverterJPA();

    @Nested
    @StructuralTest
    @DisplayName("Structural")
    class Structural {

        @Test
        @DisplayName("phone number value object -> database value")
        void phoneNumberValueObjectShouldReturnDatabaseValue() {
            MyPhoneNumber phoneNumber = MyPhoneNumber.parse("+5519998877665", "BR");

            assertThat(converter.convertToDatabaseColumn(phoneNumber)).isEqualTo("+5519998877665");
        }

        @Test
        @DisplayName("database value -> phone number value object")
        void databaseValueShouldReturnPhoneNumberValueObject() {
            MyPhoneNumber phoneNumber = converter.convertToEntityAttribute("+5519998877665");

            assertThat(phoneNumber.e164format()).isEqualTo("+5519998877665");
        }

        @Test
        @DisplayName("invalid database value -> null entity attribute")
        void invalidDatabaseValueShouldReturnNullEntityAttribute() {
            PrintStream originalErr = System.err;
            ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
            System.setErr(new PrintStream(capturedErr));

            try {
                assertThat(converter.convertToEntityAttribute("not-a-phone")).isNull();
            } finally {
                System.setErr(originalErr);
            }

            assertThat(capturedErr.toString()).contains("Erro ao converter telefone do DB: not-a-phone");
        }

        @Test
        @DisplayName("null phone number value object -> null database value")
        void nullPhoneNumberValueObjectShouldReturnNullDatabaseValue() {
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
