package br.org.gam.api.rbac.Role.domain;

import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@UnitTest
@DisplayName("Role Aggregate")
class RoleTest {

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - valid role data -> role with generated identity")
        void validRoleDataShouldCreateRoleWithGeneratedIdentity() {
            Role role = Role.register("  ADMIN  ", "  System administrator  ");

            assertThat(role.getId()).isNotNull();
            assertThat(role.getId().version()).isEqualTo(7);
            assertThat(role.getName()).isEqualTo("ADMIN");
            assertThat(role.getDescription()).isEqualTo("System administrator");
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null name -> validation error")
        void nullNameShouldReturnValidationError(String name) {
            assertThatNullPointerException()
                    .isThrownBy(() -> Role.register(name, "System administrator"))
                    .withMessage("name cannot be null");
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null description -> validation error")
        void nullDescriptionShouldReturnValidationError(String description) {
            assertThatNullPointerException()
                    .isThrownBy(() -> Role.register("ADMIN", description))
                    .withMessage("description cannot be null");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   "})
        @DisplayName("EP - blank name -> validation error")
        void blankNameShouldReturnValidationError(String name) {
            assertThatThrownBy(() -> Role.register(name, "System administrator"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("name cannot be blank");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   "})
        @DisplayName("EP - blank description -> validation error")
        void blankDescriptionShouldReturnValidationError(String description) {
            assertThatThrownBy(() -> Role.register("ADMIN", description))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("description cannot be blank");
        }
    }
}
