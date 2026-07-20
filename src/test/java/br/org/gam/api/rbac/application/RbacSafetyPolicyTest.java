package br.org.gam.api.rbac.application;

import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleRepository;
import br.org.gam.api.rbac.permission.persistence.PermissionEntity;
import br.org.gam.api.rbac.role.domain.SystemRole;
import br.org.gam.api.rbac.role.persistence.RoleEntity;
import br.org.gam.api.rbac.rolePermission.persistence.RolePermissionEntity;
import br.org.gam.api.shared.exception.ForbiddenOperationException;
import br.org.gam.api.shared.exception.NotFoundException;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("RBAC Safety Policy")
class RbacSafetyPolicyTest {

    @Mock
    private AccountRoleRepository accountRoleRepo;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("REQ-ACCOUNT-ROLE-016 - admin assigns SUDO -> rejected as system-managed")
        void adminAssignsSudoShouldBeForbidden() {
            RbacSafetyPolicy policy = new RbacSafetyPolicy(accountRoleRepo);

            assertThatThrownBy(() -> policy.assertCanAssignRoleThroughAdmin(role(SystemRole.SUDO.getCode(), true)))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("System roles are managed by their owning lifecycle or maintenance workflow.");
        }

        @ParameterizedTest
        @ValueSource(strings = {"MEMBER", "VISITOR", "COORD"})
        @DisplayName("REQ-ACCOUNT-ROLE-016 - admin assigns lifecycle-owned Role -> rejected as system-managed")
        void adminAssignsLifecycleOwnedRoleShouldBeForbidden(String roleName) {
            RbacSafetyPolicy policy = new RbacSafetyPolicy(accountRoleRepo);

            assertThatThrownBy(() -> policy.assertCanAssignRoleThroughAdmin(role(roleName, true)))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("System roles are managed by their owning lifecycle or maintenance workflow.");
        }

        @Test
        @DisplayName("REQ-ACCOUNT-ROLE-017 - admin removes SUDO -> rejected as system-managed")
        void adminRemovesSudoShouldBeForbidden() {
            RbacSafetyPolicy policy = new RbacSafetyPolicy(accountRoleRepo);
            AccountRoleEntity sudoAccountRole = accountRole(SystemRole.SUDO.getCode(), UUID.randomUUID());

            assertThatThrownBy(() -> policy.assertCanRemoveRoleThroughAdmin(sudoAccountRole))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("System roles are managed by their owning lifecycle or maintenance workflow.");
        }

        @ParameterizedTest
        @ValueSource(strings = {"MEMBER", "VISITOR", "COORD"})
        @DisplayName("REQ-ACCOUNT-ROLE-017 - admin removes lifecycle-owned Role -> rejected as system-managed")
        void adminRemovesLifecycleOwnedRoleShouldBeForbidden(String roleName) {
            RbacSafetyPolicy policy = new RbacSafetyPolicy(accountRoleRepo);
            AccountRoleEntity lifecycleAccountRole = accountRole(roleName, UUID.randomUUID());

            assertThatThrownBy(() -> policy.assertCanRemoveRoleThroughAdmin(lifecycleAccountRole))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("System roles are managed by their owning lifecycle or maintenance workflow.");
        }

        @Test
        @DisplayName("EP - internal removal of last active SUDO -> forbidden")
        void internalRemovalOfLastActiveSudoShouldBeForbidden() {
            RbacSafetyPolicy policy = new RbacSafetyPolicy(accountRoleRepo);
            UUID accountId = UUID.randomUUID();
            AccountRoleEntity sudoAccountRole = accountRole(SystemRole.SUDO.getCode(), accountId);
            when(accountRoleRepo.lockActiveAccountRolesByRoleName(SystemRole.SUDO.getCode()))
                    .thenReturn(List.of(sudoAccountRole));

            assertThatThrownBy(() -> policy.assertCanRemoveSudoThroughInternalService(sudoAccountRole))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("Cannot remove the last active SUDO account.");
        }

        @Test
        @DisplayName("EP - internal removal with another active SUDO -> allowed")
        void internalRemovalWithAnotherActiveSudoShouldBeAllowed() {
            RbacSafetyPolicy policy = new RbacSafetyPolicy(accountRoleRepo);
            AccountRoleEntity target = accountRole(SystemRole.SUDO.getCode(), UUID.randomUUID());
            AccountRoleEntity other = accountRole(SystemRole.SUDO.getCode(), UUID.randomUUID());
            when(accountRoleRepo.lockActiveAccountRolesByRoleName(SystemRole.SUDO.getCode()))
                    .thenReturn(List.of(target, other));

            assertThatCode(() -> policy.assertCanRemoveSudoThroughInternalService(target))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("REQ-ACCOUNT-ROLE-012 - internal non-SUDO removal -> forbidden")
        void internalNonSudoRemovalShouldBeForbidden() {
            RbacSafetyPolicy policy = new RbacSafetyPolicy(accountRoleRepo);
            AccountRoleEntity memberAccountRole = accountRole(SystemRole.MEMBER.getCode(), UUID.randomUUID());

            assertThatThrownBy(() -> policy.assertCanRemoveSudoThroughInternalService(memberAccountRole))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("Only SUDO role removal is allowed through this internal service.");
        }

        @Test
        @DisplayName("REQ-ACCOUNT-ROLE-012 - stale SUDO removal -> not found")
        void staleSudoRemovalShouldBeNotFound() {
            RbacSafetyPolicy policy = new RbacSafetyPolicy(accountRoleRepo);
            UUID targetAccountId = UUID.randomUUID();
            AccountRoleEntity staleTarget = accountRole(SystemRole.SUDO.getCode(), targetAccountId);
            AccountRoleEntity remainingSudo = accountRole(SystemRole.SUDO.getCode(), UUID.randomUUID());
            when(accountRoleRepo.lockActiveAccountRolesByRoleName(SystemRole.SUDO.getCode()))
                    .thenReturn(List.of(remainingSudo));

            assertThatThrownBy(() -> policy.assertCanRemoveSudoThroughInternalService(staleTarget))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("AccountRole not found with identifier "
                            + targetAccountId + ":" + staleTarget.getRole().getId());
        }

        @Test
        @DisplayName("EP - system-managed role edit -> forbidden")
        void systemManagedRoleEditShouldBeForbidden() {
            RbacSafetyPolicy policy = new RbacSafetyPolicy(accountRoleRepo);

            assertThatThrownBy(() -> policy.assertRoleCanBeManaged(role(SystemRole.MEMBER.getCode(), true)))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("System-managed roles cannot be edited, deleted, or disabled.");
        }

        @Test
        @DisplayName("EP - system-managed permission edit -> forbidden")
        void systemManagedPermissionEditShouldBeForbidden() {
            RbacSafetyPolicy policy = new RbacSafetyPolicy(accountRoleRepo);
            PermissionEntity permission = new PermissionEntity();
            permission.setSystemManaged(true);

            assertThatThrownBy(() -> policy.assertPermissionCanBeManaged(permission))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("System-managed permissions cannot be edited, deleted, or disabled.");
        }

        @Test
        @DisplayName("EP - system role-permission edit -> forbidden")
        void systemRolePermissionEditShouldBeForbidden() {
            RbacSafetyPolicy policy = new RbacSafetyPolicy(accountRoleRepo);
            RolePermissionEntity rolePermission = new RolePermissionEntity();
            rolePermission.setRole(role(SystemRole.MEMBER.getCode(), true));

            assertThatThrownBy(() -> policy.assertRolePermissionCanBeManaged(rolePermission))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("System-managed role-permission links cannot be edited.");
        }
    }

    private static AccountRoleEntity accountRole(String roleName, UUID accountId) {
        AccountRoleEntity accountRole = new AccountRoleEntity();
        accountRole.setId(UUID.randomUUID());
        accountRole.setRole(role(roleName, true));
        accountRole.setAccount(account(accountId));
        return accountRole;
    }

    private static RoleEntity role(String name, boolean systemManaged) {
        RoleEntity role = new RoleEntity();
        role.setId(UUID.randomUUID());
        role.setName(name);
        role.setDescription(name + " role");
        role.setSystemManaged(systemManaged);
        return role;
    }

    private static AccountEntity account(UUID accountId) {
        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setEmail(GamEmail.of(accountId + "@example.com"));
        account.setPasswordHash("encoded-password");
        account.setDisplayName("Account");
        return account;
    }
}
