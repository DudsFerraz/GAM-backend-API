package br.org.gam.api.account.domain;

import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@UnitTest
@DisplayName("Account Aggregate")
class AccountTest {

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - valid registration data -> account with generated identity")
        void validRegistrationDataShouldCreateAccountWithGeneratedIdentity() {
            MyEmail email = MyEmail.of("USER@example.com");

            Account account = Account.register(email, "encoded-password", " Eduardo ");

            assertThat(account.getId()).isNotNull();
            assertThat(account.getId().version()).isEqualTo(7);
            assertThat(account.getEmail()).isEqualTo(MyEmail.of("user@example.com"));
            assertThat(account.getPasswordHash()).isEqualTo("encoded-password");
            assertThat(account.getDisplayName()).isEqualTo("Eduardo");
        }

        @Test
        @DisplayName("EP - null email -> validation error")
        void nullEmailShouldReturnValidationError() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Account.register(null, "encoded-password", "Eduardo"))
                    .withMessage("Email cannot be null.");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" "})
        @DisplayName("EP - invalid password hash -> validation error")
        void invalidPasswordHashShouldReturnValidationError(String passwordHash) {
            if (passwordHash == null) {
                assertThatNullPointerException()
                        .isThrownBy(() -> Account.register(MyEmail.of("user@example.com"), null, "Eduardo"))
                        .withMessage("Password hash cannot be null.");
                return;
            }

            assertThatThrownBy(() -> Account.register(MyEmail.of("user@example.com"), passwordHash, "Eduardo"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Password hash cannot be blank.");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" "})
        @DisplayName("EP - invalid display name -> validation error")
        void invalidDisplayNameShouldReturnValidationError(String displayName) {
            if (displayName == null) {
                assertThatNullPointerException()
                        .isThrownBy(() -> Account.register(MyEmail.of("user@example.com"), "encoded-password", null))
                        .withMessage("Display name cannot be null.");
                return;
            }

            assertThatThrownBy(() -> Account.register(MyEmail.of("user@example.com"), "encoded-password", displayName))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Display name cannot be blank.");
        }
    }
}
