package br.org.gam.api.shared.domain;

import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@UnitTest
@FunctionalTest
@DisplayName("Functional - GamEmail")
class GamEmailTest {

    @ParameterizedTest
    @CsvSource({
            "user@example.com, user@example.com",
            "user.name+tag@example.com, user.name+tag@example.com",
            "' User.Name+tag@Example.COM ', user.name+tag@example.com",
            "USER@EXAMPLE.COM, user@example.com"
    })
    @DisplayName("EP - valid raw email -> normalized stored value")
    void validRawEmailShouldNormalizeStoredValue(String rawEmail, String expectedEmail) {
        GamEmail email = GamEmail.of(rawEmail);

        assertThat(email.value()).isEqualTo(expectedEmail);
        assertThat(email).hasToString(expectedEmail);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    @DisplayName("EP - absent email -> validation error")
    void absentEmailShouldReturnValidationError(String rawEmail) {
        assertCreationFails(() -> GamEmail.of(rawEmail));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "user@", "@example.com"})
    @DisplayName("EP - unusable email syntax -> validation error")
    void unusableEmailSyntaxShouldReturnValidationError(String rawEmail) {
        assertCreationFails(() -> GamEmail.of(rawEmail));
    }

    @Test
    @DisplayName("EP - provider-specific dot alias -> distinct primitive value")
    void providerSpecificDotAliasShouldRemainDistinct() {
        GamEmail dotted = GamEmail.of("first.last@example.com");
        GamEmail undotted = GamEmail.of("firstlast@example.com");

        assertThat(dotted).isNotEqualTo(undotted);
        assertThat(dotted.value()).isNotEqualTo(undotted.value());
    }

    @Test
    @DisplayName("EP - provider-specific plus alias -> distinct primitive value")
    void providerSpecificPlusAliasShouldRemainDistinct() {
        GamEmail tagged = GamEmail.of("user+tag@example.com");
        GamEmail untagged = GamEmail.of("user@example.com");

        assertThat(tagged).isNotEqualTo(untagged);
        assertThat(tagged.value()).isNotEqualTo(untagged.value());
    }

    private static void assertCreationFails(ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class);
    }
}
