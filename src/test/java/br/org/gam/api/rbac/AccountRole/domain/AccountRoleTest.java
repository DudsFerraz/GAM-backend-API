package br.org.gam.api.rbac.AccountRole.domain;

import br.org.gam.api.account.domain.Account;
import br.org.gam.api.account.domain.MyEmail;
import br.org.gam.api.rbac.Role.domain.Role;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@UnitTest
@DisplayName("Account Role Aggregate")
class AccountRoleTest {

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - valid account and role -> account role with generated identity")
        void validAccountAndRoleShouldCreateAccountRoleWithGeneratedIdentity() {
            Account account = account();
            Role role = role();

            AccountRole accountRole = AccountRole.register(account, role);

            assertThat(accountRole.getId()).isNotNull();
            assertThat(accountRole.getId().version()).isEqualTo(7);
            assertThat(accountRole.getAccount()).isSameAs(account);
            assertThat(accountRole.getRole()).isSameAs(role);
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null account -> validation error")
        void nullAccountShouldReturnValidationError(Account account) {
            assertThatNullPointerException()
                    .isThrownBy(() -> AccountRole.register(account, role()))
                    .withMessage("Account cannot be null");
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null role -> validation error")
        void nullRoleShouldReturnValidationError(Role role) {
            assertThatNullPointerException()
                    .isThrownBy(() -> AccountRole.register(account(), role))
                    .withMessage("Role cannot be null");
        }
    }

    private static Account account() {
        return Account.register(MyEmail.of("rbac-account@example.com"), "encoded-password", "RBAC Account");
    }

    private static Role role() {
        return Role.register("ADMIN", "System administrator");
    }
}
