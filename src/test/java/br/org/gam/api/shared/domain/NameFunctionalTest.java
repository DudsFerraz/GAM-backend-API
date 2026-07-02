package br.org.gam.api.shared.domain;

import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@UnitTest
@FunctionalTest
@DisplayName("Functional - Name Value Object")
class NameFunctionalTest {

    @ParameterizedTest
    @CsvSource({
            "Ana, Silva",
            "Al, Ng",
            "João Pedro, Oliveira-Santos",
            "François, L'Écuyer"
    })
    @DisplayName("EP - valid Unicode name -> accepted")
    void validUnicodeNameShouldBeAccepted(String firstName, String surname) {
        Name name = new Name(firstName, surname);

        assertThat(name.firstName()).isEqualTo(firstName);
        assertThat(name.surname()).isEqualTo(surname);
        assertThat(name.getFullName()).isEqualTo(firstName + " " + surname);
    }

    @ParameterizedTest
    @CsvSource({
            "Jose\u0301, José",
            "D\u2019Avila, D'Avila",
            "Maria\u2010Clara, Maria-Clara"
    })
    @DisplayName("EP - equivalent Unicode value -> normalized before saving")
    void equivalentUnicodeValueShouldBeNormalized(String rawFirstName, String expectedFirstName) {
        Name name = new Name(rawFirstName, "Silva");

        assertThat(name.firstName()).isEqualTo(expectedFirstName);
    }

    @ParameterizedTest
    @MethodSource("firstNamesWithMaximumLength")
    @DisplayName("BVA - first name length = 32 -> accepted")
    void firstNameLengthAtMaximumShouldBeAccepted(String firstName) {
        Name name = new Name(firstName, "Silva");

        assertThat(name.firstName()).isEqualTo(firstName);
    }

    @ParameterizedTest
    @MethodSource("surnamesWithMaximumLength")
    @DisplayName("BVA - surname length = 64 -> accepted")
    void surnameLengthAtMaximumShouldBeAccepted(String surname) {
        Name name = new Name("Maria", surname);

        assertThat(name.surname()).isEqualTo(surname);
    }

    @ParameterizedTest
    @MethodSource("firstNamesAboveMaximumLength")
    @DisplayName("BVA - first name length = 33 -> validation error")
    void firstNameAboveMaximumShouldReturnValidationError(String firstName) {
        assertThatThrownBy(() -> new Name(firstName, "Silva"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("First name cannot exceed 32 characters");
    }

    @ParameterizedTest
    @MethodSource("surnamesAboveMaximumLength")
    @DisplayName("BVA - surname length = 65 -> validation error")
    void surnameAboveMaximumShouldReturnValidationError(String surname) {
        assertThatThrownBy(() -> new Name("Maria", surname))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Last name cannot exceed 64 characters");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    @DisplayName("EP - blank first name -> validation error")
    void blankFirstNameShouldReturnValidationError(String firstName) {
        if (firstName == null) {
            assertThatNullPointerException()
                    .isThrownBy(() -> new Name(null, "Silva"))
                    .withMessage("First name cannot be null");
            return;
        }

        assertThatThrownBy(() -> new Name(firstName, "Silva"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("First or last name cannot be blank");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    @DisplayName("EP - blank surname -> validation error")
    void blankSurnameShouldReturnValidationError(String surname) {
        if (surname == null) {
            assertThatNullPointerException()
                    .isThrownBy(() -> new Name("Maria", null))
                    .withMessage("Last name cannot be null");
            return;
        }

        assertThatThrownBy(() -> new Name("Maria", surname))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("First or last name cannot be blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "A-", "A'", "A "})
    @DisplayName("BVA - first name with fewer than 2 letters -> validation error")
    void firstNameWithFewerThanTwoLettersShouldReturnValidationError(String firstName) {
        assertThatThrownBy(() -> new Name(firstName, "Silva"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"B", "B-", "B'", "B "})
    @DisplayName("BVA - surname with fewer than 2 letters -> validation error")
    void surnameWithFewerThanTwoLettersShouldReturnValidationError(String surname) {
        assertThatThrownBy(() -> new Name("Maria", surname))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Maria1", "12345", "$%&%@#Y(", "------------", "Ana.", "Ana, Maria", "Ana😀"})
    @DisplayName("EP - invalid first name format -> validation error")
    void invalidFirstNameFormatShouldReturnValidationError(String firstName) {
        assertThatThrownBy(() -> new Name(firstName, "Silva"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Use only letters, spaces, hyphens, or apostrophes.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Silva1", "12345", "$%&%@#Y(", "------------", "Silva.", "Silva, Santos", "Silva😀"})
    @DisplayName("EP - invalid surname format -> validation error")
    void invalidSurnameFormatShouldReturnValidationError(String surname) {
        assertThatThrownBy(() -> new Name("Maria", surname))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Use only letters, spaces, hyphens, or apostrophes.");
    }

    @ParameterizedTest
    @ValueSource(strings = {" Maria", "Maria ", " Maria "})
    @DisplayName("EP - first name with leading or trailing spaces -> validation error")
    void firstNameWithLeadingOrTrailingSpacesShouldReturnValidationError(String firstName) {
        assertThatThrownBy(() -> new Name(firstName, "Silva"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("First name cannot have leading or trailing spaces");
    }

    @ParameterizedTest
    @ValueSource(strings = {" Silva", "Silva ", " Silva "})
    @DisplayName("EP - surname with leading or trailing spaces -> validation error")
    void surnameWithLeadingOrTrailingSpacesShouldReturnValidationError(String surname) {
        assertThatThrownBy(() -> new Name("Maria", surname))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Last name cannot have leading or trailing spaces");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Maria  Clara", "Maria--Clara", "Maria''Clara", "Maria -Clara", "Maria- Clara"})
    @DisplayName("EP - repeated first name separators -> validation error")
    void repeatedFirstNameSeparatorsShouldReturnValidationError(String firstName) {
        assertThatThrownBy(() -> new Name(firstName, "Silva"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Use only letters, spaces, hyphens, or apostrophes.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Silva  Santos", "Silva--Santos", "Silva''Santos", "Silva -Santos", "Silva- Santos"})
    @DisplayName("EP - repeated surname separators -> validation error")
    void repeatedSurnameSeparatorsShouldReturnValidationError(String surname) {
        assertThatThrownBy(() -> new Name("Maria", surname))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Use only letters, spaces, hyphens, or apostrophes.");
    }

    private static Stream<String> firstNamesWithMaximumLength() {
        return Stream.of("a".repeat(32), "Á".repeat(32));
    }

    private static Stream<String> surnamesWithMaximumLength() {
        return Stream.of("a".repeat(64), "Á".repeat(64));
    }

    private static Stream<String> firstNamesAboveMaximumLength() {
        return Stream.of("a".repeat(33), "Á".repeat(33));
    }

    private static Stream<String> surnamesAboveMaximumLength() {
        return Stream.of("a".repeat(65), "Á".repeat(65));
    }
}
