package br.org.gam.api.Entities.account.myEmail;

import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@FunctionalTest
@DisplayName("Functional - Email Value Object")
class MyEmailFunctionalTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest
    @CsvSource({
            "user@example.com, user@example.com",
            "' User.Name+tag@Example.COM ', user.name+tag@example.com"
    })
    @DisplayName("EP - valid email -> normalized lowercase trimmed value")
    void validEmailShouldNormalizeValue(String rawEmail, String expectedEmail) {
        MyEmail email = MyEmail.of(rawEmail);

        assertThat(email.value()).isEqualTo(expectedEmail);
    }

    @Test
    @DisplayName("EP - null email -> bean validation error")
    void nullEmailShouldReturnValidationError() {
        MyEmail email = MyEmail.of(null);

        assertThat(validator.validate(email))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("value");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-an-email", "user@", "@example.com"})
    @DisplayName("EP - invalid email -> bean validation error")
    void invalidEmailShouldReturnValidationError(String rawEmail) {
        MyEmail email = MyEmail.of(rawEmail);

        assertThat(validator.validate(email)).isNotEmpty();
    }
}
