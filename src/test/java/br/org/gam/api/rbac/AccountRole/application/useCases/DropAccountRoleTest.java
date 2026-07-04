package br.org.gam.api.rbac.AccountRole.application.useCases;

import br.org.gam.api.rbac.AccountRole.application.AccountRoleDTO;
import br.org.gam.api.rbac.AccountRole.application.AccountRoleEntityLoader;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleRepository;
import br.org.gam.api.rbac.Role.application.RoleEntityLoader;
import br.org.gam.api.rbac.Role.persistence.RoleEntity;
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
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
            entity.setRole(role);

            when(getAccountRoleInstance.requiredByDTO(dto)).thenReturn(entity);

            dropAccountRole.byDTO(dto);

            verify(accountRoleRepo).delete(entity);
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

            verifyNoInteractions(getAccountRoleInstance, activityEvents);
            verify(accountRoleRepo, never()).delete(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("EP - missing account role -> not found error")
        void missingAccountRoleShouldReturnNotFoundError() {
            AccountRoleDTO dto = new AccountRoleDTO(UUID.randomUUID(), UUID.randomUUID(), "Remove missing role");

            when(getAccountRoleInstance.requiredByDTO(dto))
                    .thenThrow(NotFoundException.resource("AccountRole", dto.accountId() + ":" + dto.roleId()));

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
            AccountRoleEntity accountRoleEntity = new AccountRoleEntity();
            accountRoleEntity.setId(UUID.randomUUID());
            accountRoleEntity.setRole(roleEntity);

            when(getRoleInstance.requiredByName("ADMIN")).thenReturn(roleEntity);
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
    }
}
