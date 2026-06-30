package br.org.gam.api.common.myPhoneNumber;

import br.org.gam.api.exception.InvalidPhoneNumberException;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@UnitTest
@FunctionalTest
@DisplayName("Functional - Phone Number Value Object")
class MyPhoneNumberFunctionalTest {

    @ParameterizedTest
    @ValueSource(strings = {"+5519998877665", "(19) 99887-7665", "19998877665"})
    @DisplayName("EP - valid Brazilian phone number -> normalized formats")
    void validBrazilianPhoneNumberShouldNormalizeFormats(String rawPhoneNumber) {
        MyPhoneNumber phoneNumber = MyPhoneNumber.parse(rawPhoneNumber, "BR");

        assertThat(phoneNumber.e164format()).isEqualTo("+5519998877665");
        assertThat(phoneNumber.countryCode()).isEqualTo(55);
        assertThat(phoneNumber.nationalNumber()).isEqualTo(19998877665L);
        assertThat(phoneNumber.toString()).isEqualTo("+5519998877665");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("EP - blank phone number -> validation error")
    void blankPhoneNumberShouldReturnValidationError(String rawPhoneNumber) {
        assertThatThrownBy(() -> MyPhoneNumber.parse(rawPhoneNumber, "BR"))
                .isInstanceOf(InvalidPhoneNumberException.class)
                .hasMessage("Phone number cannot be null or blank.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "+5500000000000"})
    @DisplayName("EP - malformed phone number -> validation error")
    void malformedPhoneNumberShouldReturnValidationError(String rawPhoneNumber) {
        assertThatThrownBy(() -> MyPhoneNumber.parse(rawPhoneNumber, "BR"))
                .isInstanceOf(InvalidPhoneNumberException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"+5519998877665", "(19) 99887-7665"})
    @DisplayName("EP - valid JSON value -> default Brazilian phone number")
    void validJsonValueShouldReturnDefaultBrazilianPhoneNumber(String rawPhoneNumber) {
        MyPhoneNumber phoneNumber = MyPhoneNumber.fromString(rawPhoneNumber);

        assertThat(phoneNumber.e164format()).isEqualTo("+5519998877665");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc"})
    @DisplayName("EP - invalid JSON value -> argument error")
    void invalidJsonValueShouldReturnArgumentError(String rawPhoneNumber) {
        assertThatThrownBy(() -> MyPhoneNumber.fromString(rawPhoneNumber))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
