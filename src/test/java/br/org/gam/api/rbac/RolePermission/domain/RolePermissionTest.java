package br.org.gam.api.rbac.RolePermission.domain;

import br.org.gam.api.rbac.Permission.domain.Permission;
import br.org.gam.api.rbac.Permission.persistence.PermissionEntity;
import br.org.gam.api.rbac.Role.domain.Role;
import br.org.gam.api.rbac.Role.persistence.RoleEntity;
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
@DisplayName("Role Permission Aggregate")
class RolePermissionTest {

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - valid role and permission -> role permission with generated identity")
        void validRoleAndPermissionShouldCreateRolePermissionWithGeneratedIdentity() {
            RoleEntity role = new RoleEntity();
            PermissionEntity permission = new PermissionEntity();

            RolePermission rolePermission = RolePermission.register(role, permission);

            assertThat(rolePermission.getId()).isNotNull();
            assertThat(rolePermission.getId().version()).isEqualTo(7);
            assertThat(rolePermission.getRole()).isSameAs(role);
            assertThat(rolePermission.getPermission()).isSameAs(permission);
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null role -> validation error")
        void nullRoleShouldReturnValidationError(RoleEntity role) {
            assertThatNullPointerException()
                    .isThrownBy(() -> RolePermission.register(role, new PermissionEntity()))
                    .withMessage("role cannot be null");
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null permission -> validation error")
        void nullPermissionShouldReturnValidationError(PermissionEntity permission) {
            assertThatNullPointerException()
                    .isThrownBy(() -> RolePermission.register(new RoleEntity(), permission))
                    .withMessage("permission cannot be null");
        }
    }
}
