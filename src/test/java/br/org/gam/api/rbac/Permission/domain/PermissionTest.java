package br.org.gam.api.rbac.Permission.domain;

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
@DisplayName("Permission Aggregate")
class PermissionTest {

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - valid permission data -> permission with generated identity")
        void validPermissionDataShouldCreatePermissionWithGeneratedIdentity() {
            Permission permission = Permission.register("  MEMBER_GET  ", "  View active members  ");

            assertThat(permission.getId()).isNotNull();
            assertThat(permission.getId().version()).isEqualTo(7);
            assertThat(permission.getName()).isEqualTo("MEMBER_GET");
            assertThat(permission.getDescription()).isEqualTo("View active members");
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null name -> validation error")
        void nullNameShouldReturnValidationError(String name) {
            assertThatNullPointerException()
                    .isThrownBy(() -> Permission.register(name, "View active members"))
                    .withMessage("name cannot be null");
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null description -> validation error")
        void nullDescriptionShouldReturnValidationError(String description) {
            assertThatNullPointerException()
                    .isThrownBy(() -> Permission.register("MEMBER_GET", description))
                    .withMessage("description cannot be null");
        }
    }
}
