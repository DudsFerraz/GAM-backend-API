package br.org.gam.api.shared.domain;

import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@UnitTest
@FunctionalTest
@DisplayName("Functional - GamName")
class GamNameTest {

    @ParameterizedTest
    @MethodSource("validNames")
    @DisplayName("EP - valid firstName and surname -> accepted with full-name rendering")
    void validFirstNameAndSurnameShouldBeAccepted(String firstName, String surname, String expectedFullName) {
        GamName name = new GamName(firstName, surname);

        assertThat(name.firstName()).isEqualTo(firstName);
        assertThat(name.surname()).isEqualTo(surname);
        assertThat(name.getFullName()).isEqualTo(expectedFullName);
        assertThat(name).hasToString(expectedFullName);
    }

    @ParameterizedTest
    @MethodSource("equivalentRepresentations")
    @DisplayName("EP - equivalent Unicode or separator representation -> canonical stored value")
    void equivalentRepresentationsShouldBeNormalized(
            String rawFirstName,
            String rawSurname,
            String expectedFirstName,
            String expectedSurname
    ) {
        GamName name = new GamName(rawFirstName, rawSurname);

        assertThat(name.firstName()).isEqualTo(expectedFirstName);
        assertThat(name.surname()).isEqualTo(expectedSurname);
    }

    @ParameterizedTest
    @MethodSource("firstNamesAtMaximumLength")
    @DisplayName("BVA - firstName length = 32 -> accepted")
    void firstNameAtMaximumLengthShouldBeAccepted(String firstName) {
        GamName name = new GamName(firstName, "Silva");

        assertThat(name.firstName()).isEqualTo(firstName);
    }

    @ParameterizedTest
    @MethodSource("surnamesAtMaximumLength")
    @DisplayName("BVA - surname length = 64 -> accepted")
    void surnameAtMaximumLengthShouldBeAccepted(String surname) {
        GamName name = new GamName("Maria", surname);

        assertThat(name.surname()).isEqualTo(surname);
    }

    @ParameterizedTest
    @MethodSource("firstNamesAboveMaximumLength")
    @DisplayName("BVA - firstName length = 33 -> validation error")
    void firstNameAboveMaximumLengthShouldReturnValidationError(String firstName) {
        assertCreationFails(() -> new GamName(firstName, "Silva"));
    }

    @ParameterizedTest
    @MethodSource("surnamesAboveMaximumLength")
    @DisplayName("BVA - surname length = 65 -> validation error")
    void surnameAboveMaximumLengthShouldReturnValidationError(String surname) {
        assertCreationFails(() -> new GamName("Maria", surname));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    @DisplayName("EP - absent firstName -> validation error")
    void absentFirstNameShouldReturnValidationError(String firstName) {
        assertCreationFails(() -> new GamName(firstName, "Silva"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    @DisplayName("EP - absent surname -> validation error")
    void absentSurnameShouldReturnValidationError(String surname) {
        assertCreationFails(() -> new GamName("Maria", surname));
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "A-", "A'"})
    @DisplayName("BVA - firstName with fewer than 2 Unicode letters -> validation error")
    void firstNameWithFewerThanTwoLettersFromMovedCoverageShouldReturnValidationError(String firstName) {
        assertCreationFails(() -> new GamName(firstName, "Silva"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"B", "B-", "B'"})
    @DisplayName("BVA - surname with fewer than 2 Unicode letters -> validation error")
    void surnameWithFewerThanTwoLettersFromMovedCoverageShouldReturnValidationError(String surname) {
        assertCreationFails(() -> new GamName("Maria", surname));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Maria1", "12345", "Ana.", "Ana, Maria", "Ana\uD83D\uDE00"})
    @DisplayName("EP - firstName with disallowed characters -> validation error")
    void firstNameWithDisallowedCharactersShouldReturnValidationError(String firstName) {
        assertCreationFails(() -> new GamName(firstName, "Silva"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Silva1", "12345", "Silva.", "Silva, Santos", "Silva\uD83D\uDE00"})
    @DisplayName("EP - surname with disallowed characters -> validation error")
    void surnameWithDisallowedCharactersShouldReturnValidationError(String surname) {
        assertCreationFails(() -> new GamName("Maria", surname));
    }

    @ParameterizedTest
    @ValueSource(strings = {" Maria", "Maria ", " Maria "})
    @DisplayName("EP - firstName with leading or trailing whitespace -> validation error")
    void firstNameWithLeadingOrTrailingWhitespaceShouldReturnValidationError(String firstName) {
        assertCreationFails(() -> new GamName(firstName, "Silva"));
    }

    @ParameterizedTest
    @ValueSource(strings = {" Silva", "Silva ", " Silva "})
    @DisplayName("EP - surname with leading or trailing whitespace -> validation error")
    void surnameWithLeadingOrTrailingWhitespaceShouldReturnValidationError(String surname) {
        assertCreationFails(() -> new GamName("Maria", surname));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Maria  Clara", "Maria--Clara", "Maria''Clara", "Maria -Clara", "Maria- Clara", "-Maria"})
    @DisplayName("EP - firstName with repeated, leading, trailing, or combined separators -> validation error")
    void firstNameWithInvalidSeparatorsShouldReturnValidationError(String firstName) {
        assertCreationFails(() -> new GamName(firstName, "Silva"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Silva  Santos", "Silva--Santos", "Silva''Santos", "Silva -Santos", "Silva- Santos", "Silva-"})
    @DisplayName("EP - surname with repeated, leading, trailing, or combined separators -> validation error")
    void surnameWithInvalidSeparatorsShouldReturnValidationError(String surname) {
        assertCreationFails(() -> new GamName("Maria", surname));
    }

    @ParameterizedTest
    @CsvSource({
            "Ana, Silva",
            "Al, Ng",
            "Joao Pedro, Oliveira-Santos",
            "Francois, L'Ecuyer"
    })
    @DisplayName("EP - valid Unicode name -> accepted")
    void validUnicodeNameShouldBeAccepted(String firstName, String surname) {
        GamName name = new GamName(firstName, surname);

        assertThat(name.firstName()).isEqualTo(firstName);
        assertThat(name.surname()).isEqualTo(surname);
        assertThat(name.getFullName()).isEqualTo(firstName + " " + surname);
    }

    @ParameterizedTest
    @CsvSource({
            "Jose\u0301, Jos\u00E9",
            "D\u2019Avila, D'Avila",
            "Maria\u2010Clara, Maria-Clara"
    })
    @DisplayName("EP - equivalent Unicode value -> normalized before saving")
    void equivalentUnicodeValueShouldBeNormalized(String rawFirstName, String expectedFirstName) {
        GamName name = new GamName(rawFirstName, "Silva");

        assertThat(name.firstName()).isEqualTo(expectedFirstName);
    }

    @ParameterizedTest
    @MethodSource("firstNamesWithMaximumLength")
    @DisplayName("BVA - firstName length = 32 -> accepted")
    void firstNameLengthAtMaximumShouldBeAccepted(String firstName) {
        GamName name = new GamName(firstName, "Silva");

        assertThat(name.firstName()).isEqualTo(firstName);
    }

    @ParameterizedTest
    @MethodSource("surnamesWithMaximumLength")
    @DisplayName("BVA - surname length = 64 -> accepted")
    void surnameLengthAtMaximumShouldBeAccepted(String surname) {
        GamName name = new GamName("Maria", surname);

        assertThat(name.surname()).isEqualTo(surname);
    }

    @ParameterizedTest
    @MethodSource("firstNamesAboveMaximumLength")
    @DisplayName("BVA - firstName length = 33 -> validation error")
    void firstNameAboveMaximumShouldReturnValidationError(String firstName) {
        assertThatThrownBy(() -> new GamName(firstName, "Silva"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("surnamesAboveMaximumLength")
    @DisplayName("BVA - surname length = 65 -> validation error")
    void surnameAboveMaximumShouldReturnValidationError(String surname) {
        assertThatThrownBy(() -> new GamName("Maria", surname))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    @DisplayName("EP - blank firstName -> validation error")
    void blankFirstNameShouldReturnValidationError(String firstName) {
        assertThatThrownBy(() -> new GamName(firstName, "Silva"))
                .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    @DisplayName("EP - blank surname -> validation error")
    void blankSurnameShouldReturnValidationError(String surname) {
        assertThatThrownBy(() -> new GamName("Maria", surname))
                .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "A-", "A'"})
    @DisplayName("BVA - firstName with fewer than 2 letters -> validation error")
    void firstNameWithFewerThanTwoLettersShouldReturnValidationError(String firstName) {
        assertThatThrownBy(() -> new GamName(firstName, "Silva"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"B", "B-", "B'"})
    @DisplayName("BVA - surname with fewer than 2 letters -> validation error")
    void surnameWithFewerThanTwoLettersShouldReturnValidationError(String surname) {
        assertThatThrownBy(() -> new GamName("Maria", surname))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Maria1", "12345", "$%&%@#Y(", "------------", "Ana.", "Ana, Maria", "Ana\uD83D\uDE00"})
    @DisplayName("EP - invalid firstName format -> validation error")
    void invalidFirstNameFormatShouldReturnValidationError(String firstName) {
        assertThatThrownBy(() -> new GamName(firstName, "Silva"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Silva1", "12345", "$%&%@#Y(", "------------", "Silva.", "Silva, Santos", "Silva\uD83D\uDE00"})
    @DisplayName("EP - invalid surname format -> validation error")
    void invalidSurnameFormatShouldReturnValidationError(String surname) {
        assertThatThrownBy(() -> new GamName("Maria", surname))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {" Maria", "Maria ", " Maria "})
    @DisplayName("EP - firstName with leading or trailing spaces -> validation error")
    void firstNameWithLeadingOrTrailingSpacesShouldReturnValidationError(String firstName) {
        assertThatThrownBy(() -> new GamName(firstName, "Silva"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {" Silva", "Silva ", " Silva "})
    @DisplayName("EP - surname with leading or trailing spaces -> validation error")
    void surnameWithLeadingOrTrailingSpacesShouldReturnValidationError(String surname) {
        assertThatThrownBy(() -> new GamName("Maria", surname))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Maria  Clara", "Maria--Clara", "Maria''Clara", "Maria -Clara", "Maria- Clara"})
    @DisplayName("EP - repeated firstName separators -> validation error")
    void repeatedFirstNameSeparatorsShouldReturnValidationError(String firstName) {
        assertThatThrownBy(() -> new GamName(firstName, "Silva"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Silva  Santos", "Silva--Santos", "Silva''Santos", "Silva -Santos", "Silva- Santos"})
    @DisplayName("EP - repeated surname separators -> validation error")
    void repeatedSurnameSeparatorsShouldReturnValidationError(String surname) {
        assertThatThrownBy(() -> new GamName("Maria", surname))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertCreationFails(ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class);
    }

    private static Stream<Arguments> validNames() {
        return Stream.of(
                Arguments.of("Ana", "Silva", "Ana Silva"),
                Arguments.of("Al", "Ng", "Al Ng"),
                Arguments.of("Joao Pedro", "Oliveira-Santos", "Joao Pedro Oliveira-Santos"),
                Arguments.of("Lia", "D'Avila", "Lia D'Avila")
        );
    }

    private static Stream<Arguments> equivalentRepresentations() {
        return Stream.of(
                Arguments.of("Jose\u0301", "Silva", "Jos\u00E9", "Silva"),
                Arguments.of("Lia", "D\u2019Avila", "Lia", "D'Avila"),
                Arguments.of("Maria\u2010Clara", "Silva", "Maria-Clara", "Silva")
        );
    }

    private static Stream<String> firstNamesAtMaximumLength() {
        return Stream.of("a".repeat(32), "\u00C1".repeat(32));
    }

    private static Stream<String> surnamesAtMaximumLength() {
        return Stream.of("a".repeat(64), "\u00C1".repeat(64));
    }

    private static Stream<String> firstNamesAboveMaximumLength() {
        return Stream.of("a".repeat(33), "\u00C1".repeat(33));
    }

    private static Stream<String> surnamesAboveMaximumLength() {
        return Stream.of("a".repeat(65), "\u00C1".repeat(65));
    }

    private static Stream<String> firstNamesWithMaximumLength() {
        return Stream.of("a".repeat(32), "\u00C1".repeat(32));
    }

    private static Stream<String> surnamesWithMaximumLength() {
        return Stream.of("a".repeat(64), "\u00C1".repeat(64));
    }
}
