package br.org.gam.api.shared.phonenumber;

import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@UnitTest
@FunctionalTest
@DisplayName("Functional - GamPhoneNumber")
class GamPhoneNumberTest {

    @ParameterizedTest
    @ValueSource(strings = {"+5519998877665", "(19) 99887-7665", "19998877665"})
    @DisplayName("EP - valid Brazilian phone number -> canonical E.164 value")
    void validBrazilianPhoneNumberShouldExposeCanonicalE164Value(String rawPhoneNumber) {
        GamPhoneNumber phoneNumber = GamPhoneNumber.fromString(rawPhoneNumber);

        assertThat(phoneNumber.value()).isEqualTo("+5519998877665");
        assertThat(phoneNumber).hasToString("+5519998877665");
    }

    @ParameterizedTest
    @ValueSource(strings = {"+14155552671", "+442071838750"})
    @DisplayName("EP - valid explicit international phone number -> accepted")
    void validExplicitInternationalPhoneNumberShouldBeAccepted(String rawPhoneNumber) {
        GamPhoneNumber phoneNumber = GamPhoneNumber.fromString(rawPhoneNumber);

        assertThat(phoneNumber.value()).isEqualTo(rawPhoneNumber);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    @DisplayName("EP - absent phone number -> validation error")
    void absentPhoneNumberShouldReturnValidationError(String rawPhoneNumber) {
        assertCreationFails(() -> GamPhoneNumber.fromString(rawPhoneNumber));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "+5500000000000", "2025550125"})
    @DisplayName("EP - unparseable or non-dialable phone number -> validation error")
    void unparseableOrNonDialablePhoneNumberShouldReturnValidationError(String rawPhoneNumber) {
        assertCreationFails(() -> GamPhoneNumber.fromString(rawPhoneNumber));
    }

    private static void assertCreationFails(ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class);
    }
}
