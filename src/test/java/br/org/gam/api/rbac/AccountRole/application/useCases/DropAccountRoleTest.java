package br.org.gam.api.rbac.accountRole.application.useCases;

import br.org.gam.api.rbac.accountRole.application.AccountRoleDTO;
import br.org.gam.api.rbac.accountRole.application.AccountRoleEntityLoader;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleRepository;
import br.org.gam.api.rbac.role.application.RoleEntityLoader;
import br.org.gam.api.rbac.role.persistence.RoleEntity;
import br.org.gam.api.rbac.application.RbacSafetyPolicy;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.exception.InvalidCommandException;
import br.org.gam.api.shared.exception.NotFoundException;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Drop Account Role Use Case")
class DropAccountRoleTest {

    @Mock
    private AccountRoleEntityLoader getAccountRoleInstance;

    @Mock
    private AccountRoleRepository accountRoleRepo;

    @Mock
    private RoleEntityLoader getRoleInstance;

    @Mock
    private ActivityEvents activityEvents;

    @Mock
    private RbacSafetyPolicy rbacSafetyPolicy;

    @InjectMocks
    private DropAccountRole dropAccountRole;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - existing account role -> account role is deleted")
        void existingAccountRoleShouldDeleteAccountRole() {
            AccountRoleDTO dto = new AccountRoleDTO(UUID.randomUUID(), UUID.randomUUID(), "Remove admin access");
            AccountRoleEntity entity = new AccountRoleEntity();
            entity.setId(UUID.randomUUID());
            RoleEntity role = new RoleEntity();
            role.setName("ADMIN");
            role.setSystemManaged(false);
            entity.setRole(role);

            when(getRoleInstance.requiredById(dto.roleId())).thenReturn(role);
            when(getAccountRoleInstance.requiredByDTO(dto)).thenReturn(entity);

            dropAccountRole.byDTO(dto);

            verify(accountRoleRepo).delete(entity);
            verify(rbacSafetyPolicy).assertCanRemoveRoleThroughAdmin(role);
            verify(activityEvents).accountRoleRemoved(
                    entity.getId(), dto.accountId(), dto.roleId(), "ADMIN", "Remove admin access");
        }

        @Test
        @DisplayName("EP - account role remove without reason -> validation error")
        void accountRoleRemoveWithoutReasonShouldReturnValidationError() {
            AccountRoleDTO dto = new AccountRoleDTO(UUID.randomUUID(), UUID.randomUUID(), " ");

            assertThatThrownBy(() -> dropAccountRole.byDTO(dto))
                    .isInstanceOf(InvalidCommandException.class)
                    .hasMessage("Account role changes require an audit reason.");

            verifyNoInteractions(getAccountRoleInstance, activityEvents, rbacSafetyPolicy);
            verify(accountRoleRepo, never()).delete(org.mockito.ArgumentMatchers.any());
        }

        @ParameterizedTest
        @MethodSource("invalidReasons")
        @DisplayName("REQ-ACCOUNT-ROLE-005 - invalid reason -> rejected before loading or mutation")
        void invalidReasonShouldBeRejectedBeforeLoadingOrMutation(String reason) {
            AccountRoleDTO dto = new AccountRoleDTO(UUID.randomUUID(), UUID.randomUUID(), reason);

            assertThatThrownBy(() -> dropAccountRole.byDTO(dto))
                    .isInstanceOf(InvalidCommandException.class);

            verifyNoInteractions(
                    getAccountRoleInstance,
                    accountRoleRepo,
                    getRoleInstance,
                    activityEvents,
                    rbacSafetyPolicy
            );
        }

        @Test
        @DisplayName("REQ-ACCOUNT-ROLE-005 - maximum reason length -> accepted after trimming")
        void maximumReasonLengthShouldBeAcceptedAfterTrimming() {
            UUID accountId = UUID.randomUUID();
            UUID roleId = UUID.randomUUID();
            String reason = "a".repeat(2_000);
            AccountRoleDTO dto = new AccountRoleDTO(accountId, roleId, " " + reason + " ");
            AccountRoleEntity entity = new AccountRoleEntity();
            entity.setId(UUID.randomUUID());
            RoleEntity role = new RoleEntity();
            role.setId(roleId);
            role.setName("ADMIN");
            role.setSystemManaged(false);
            entity.setRole(role);

            when(getRoleInstance.requiredById(roleId)).thenReturn(role);
            when(getAccountRoleInstance.requiredByDTO(dto)).thenReturn(entity);

            dropAccountRole.byDTO(dto);

            verify(activityEvents).accountRoleRemoved(
                    entity.getId(), accountId, roleId, "ADMIN", reason);
        }

        @Test
        @DisplayName("REQ-ACCOUNT-ROLE-007 and ADR-0004 - generic drop exposes no audit or safety bypass")
        void genericDropShouldNotExposeAuditOrSafetyBypass() {
            assertThat(Stream.of(DropAccountRole.class.getDeclaredMethods()))
                    .noneMatch(method -> Stream.of(method.getParameterTypes())
                            .anyMatch(type -> type == boolean.class || type == Boolean.class));
        }

        @Test
        @DisplayName("EP - missing account role -> not found error")
        void missingAccountRoleShouldReturnNotFoundError() {
            AccountRoleDTO dto = new AccountRoleDTO(UUID.randomUUID(), UUID.randomUUID(), "Remove missing role");

            when(getAccountRoleInstance.requiredByDTO(dto))
                    .thenThrow(NotFoundException.resource("AccountRole", dto.accountId() + ":" + dto.roleId()));
            RoleEntity customRole = new RoleEntity();
            customRole.setId(dto.roleId());
            customRole.setName("CUSTOM");
            customRole.setSystemManaged(false);
            when(getRoleInstance.requiredById(dto.roleId())).thenReturn(customRole);

            assertThatThrownBy(() -> dropAccountRole.byDTO(dto))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("AccountRole not found with identifier " + dto.accountId() + ":" + dto.roleId());

            verify(accountRoleRepo, never()).delete(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("EP - role name and account id -> account role is deleted")
        void roleNameAndAccountIdShouldDeleteAccountRole() {
            UUID accountId = UUID.randomUUID();
            UUID roleId = UUID.randomUUID();
            RoleEntity roleEntity = new RoleEntity();
            roleEntity.setId(roleId);
            roleEntity.setName("ADMIN");
            roleEntity.setSystemManaged(false);
            AccountRoleEntity accountRoleEntity = new AccountRoleEntity();
            accountRoleEntity.setId(UUID.randomUUID());
            accountRoleEntity.setRole(roleEntity);

            when(getRoleInstance.requiredByName("ADMIN")).thenReturn(roleEntity);
            when(getRoleInstance.requiredById(roleId)).thenReturn(roleEntity);
            AccountRoleDTO dto = new AccountRoleDTO(accountId, roleId, "Remove admin access");
            when(getAccountRoleInstance.requiredByDTO(dto)).thenReturn(accountRoleEntity);

            dropAccountRole.byRoleName("ADMIN", accountId, "Remove admin access");

            verify(getRoleInstance).requiredByName("ADMIN");
            verify(accountRoleRepo).delete(accountRoleEntity);
            verify(activityEvents).accountRoleRemoved(
                    accountRoleEntity.getId(), accountId, roleId, "ADMIN", "Remove admin access");
        }

        @Test
        @DisplayName("EP - missing role name -> not found error")
        void missingRoleNameShouldReturnNotFoundError() {
            UUID accountId = UUID.randomUUID();

            when(getRoleInstance.requiredByName("ADMIN"))
                    .thenThrow(NotFoundException.resource("Role", "ADMIN"));

            assertThatThrownBy(() -> dropAccountRole.byRoleName("ADMIN", accountId, "Remove missing role"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Role not found with identifier ADMIN");

            verifyNoInteractions(getAccountRoleInstance);
            verify(accountRoleRepo, never()).delete(org.mockito.ArgumentMatchers.any());
        }

        private static Stream<String> invalidReasons() {
            return Stream.of(null, "", " \n\t", "a".repeat(2_001));
        }
    }
}
