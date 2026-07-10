package br.org.gam.api.shared.phonenumber;

import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import jakarta.persistence.Converter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@UnitTest
@DisplayName("GamPhoneNumber JPA Converter")
class GamPhoneNumberConverterJPATest {

    private final GamPhoneNumberConverterJPA converter = new GamPhoneNumberConverterJPA();

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - phone number -> database E.164 value")
        void phoneNumberShouldConvertToDatabaseValue() {
            GamPhoneNumber phoneNumber = GamPhoneNumber.fromString("+5519998877665");

            assertThat(converter.convertToDatabaseColumn(phoneNumber)).isEqualTo("+5519998877665");
        }

        @Test
        @DisplayName("EP - database E.164 value -> phone number")
        void databaseValueShouldConvertToPhoneNumber() {
            GamPhoneNumber phoneNumber = converter.convertToEntityAttribute("+5519998877665");

            assertThat(phoneNumber.value()).isEqualTo("+5519998877665");
        }

        @Test
        @DisplayName("EP - null phone number -> null database value")
        void nullPhoneNumberShouldConvertToNullDatabaseValue() {
            assertThat(converter.convertToDatabaseColumn(null)).isNull();
        }

        @Test
        @DisplayName("EP - null database value -> null phone number")
        void nullDatabaseValueShouldConvertToNullPhoneNumber() {
            assertThat(converter.convertToEntityAttribute(null)).isNull();
        }

        @ParameterizedTest
        @EmptySource
        @ValueSource(strings = {" ", "   ", "\t"})
        @DisplayName("EP - blank database value -> null phone number")
        void blankDatabaseValueShouldConvertToNullPhoneNumber(String dbData) {
            assertThat(converter.convertToEntityAttribute(dbData)).isNull();
        }

        @Test
        @DisplayName("EP - invalid database value -> conversion error")
        void invalidDatabaseValueShouldReturnConversionError() {
            assertThatThrownBy(() -> converter.convertToEntityAttribute("not-a-phone"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @StructuralTest
    @DisplayName("Structural")
    class Structural {

        @Test
        @DisplayName("COND - JPA converter -> auto-apply converter")
        void jpaConverterShouldBeAutoApplyConverter() {
            Converter annotation = GamPhoneNumberConverterJPA.class.getAnnotation(Converter.class);

            assertThat(annotation).isNotNull();
            assertThat(annotation.autoApply()).isTrue();
        }
    }
}
