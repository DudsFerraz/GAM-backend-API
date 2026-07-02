package br.org.gam.api.rbac.AccountRole.application.useCases.AddAccountRole;

import br.org.gam.api.account.application.AccountNotFoundException;
import br.org.gam.api.account.application.useCases.GetAccountInstance.GetAccountInstance;
import br.org.gam.api.account.domain.MyEmail;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.rbac.AccountRole.application.AccountAlreadyHasRoleException;
import br.org.gam.api.rbac.AccountRole.application.AccountRoleDTO;
import br.org.gam.api.rbac.AccountRole.application.AccountRoleMapper;
import br.org.gam.api.rbac.AccountRole.application.AccountRoleRDTO;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleRepository;
import br.org.gam.api.rbac.Role.application.RoleNotFoundException;
import br.org.gam.api.rbac.Role.application.RoleRDTO;
import br.org.gam.api.rbac.Role.application.useCases.GetRoleInstance.GetRoleInstance;
import br.org.gam.api.rbac.Role.persistence.RoleEntity;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Add Account Role Use Case")
class AddAccountRoleTest {

    @Mock
    private AccountRoleRepository accountRoleRepo;

    @Mock
    private GetAccountInstance getAccountInstance;

    @Mock
    private GetRoleInstance getRoleInstance;

    @Mock
    private AccountRoleMapper accountRoleMapper;

    @InjectMocks
    private AddAccountRole addAccountRole;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - account without role -> account role is added")
        void accountWithoutRoleShouldAddAccountRole() {
            AccountEntity account = account();
            RoleEntity role = role();
            AccountRoleDTO dto = new AccountRoleDTO(account.getId(), role.getId());
            AccountRoleEntity savedEntity = new AccountRoleEntity();
            AccountRoleRDTO expectedResponse = response(role.getId());

            when(getAccountInstance.entityById(dto.accountId())).thenReturn(account);
            when(getRoleInstance.entityById(dto.roleId())).thenReturn(role);
            when(accountRoleRepo.existsByAccount_IdAndRole_Id(account.getId(), role.getId())).thenReturn(false);
            when(accountRoleRepo.save(anyAccountRoleEntity())).thenReturn(savedEntity);
            when(accountRoleMapper.entityToAccountRoleRDTO(savedEntity)).thenReturn(expectedResponse);

            AccountRoleRDTO response = addAccountRole.byDTO(dto);

            assertThat(response).isSameAs(expectedResponse);

            ArgumentCaptor<AccountRoleEntity> accountRoleCaptor = ArgumentCaptor.forClass(AccountRoleEntity.class);
            verify(accountRoleRepo).save(accountRoleCaptor.capture());
            AccountRoleEntity accountRole = accountRoleCaptor.getValue();
            assertThat(accountRole.getId()).isNotNull();
            assertThat(accountRole.getId().version()).isEqualTo(7);
            assertThat(accountRole.getAccount()).isSameAs(account);
            assertThat(accountRole.getRole()).isSameAs(role);
        }

        @Test
        @DisplayName("EP - account already has role -> conflict error")
        void accountAlreadyHasRoleShouldReturnConflictError() {
            AccountEntity account = account();
            RoleEntity role = role();
            AccountRoleDTO dto = new AccountRoleDTO(account.getId(), role.getId());

            when(getAccountInstance.entityById(dto.accountId())).thenReturn(account);
            when(getRoleInstance.entityById(dto.roleId())).thenReturn(role);
            when(accountRoleRepo.existsByAccount_IdAndRole_Id(account.getId(), role.getId())).thenReturn(true);

            assertThatThrownBy(() -> addAccountRole.byDTO(dto))
                    .isInstanceOf(AccountAlreadyHasRoleException.class)
                    .hasMessage("Account: " + account.getEmail() + " already has role: " + role.getName());

            verifyNoInteractions(accountRoleMapper);
            verify(accountRoleRepo, never()).save(any());
        }

        @Test
        @DisplayName("EP - missing account id -> not found error")
        void missingAccountIdShouldReturnNotFoundError() {
            UUID accountId = UUID.randomUUID();
            UUID roleId = UUID.randomUUID();
            AccountRoleDTO dto = new AccountRoleDTO(accountId, roleId);

            when(getAccountInstance.entityById(accountId))
                    .thenThrow(new AccountNotFoundException("Could not find account with id " + accountId));

            assertThatThrownBy(() -> addAccountRole.byDTO(dto))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessage("Could not find account with id " + accountId);

            verifyNoInteractions(getRoleInstance, accountRoleMapper);
            verify(accountRoleRepo, never()).save(any());
        }

        @Test
        @DisplayName("EP - missing role id -> not found error")
        void missingRoleIdShouldReturnNotFoundError() {
            AccountEntity account = account();
            UUID roleId = UUID.randomUUID();
            AccountRoleDTO dto = new AccountRoleDTO(account.getId(), roleId);

            when(getAccountInstance.entityById(account.getId())).thenReturn(account);
            when(getRoleInstance.entityById(roleId))
                    .thenThrow(new RoleNotFoundException("Could not find role with id " + roleId));

            assertThatThrownBy(() -> addAccountRole.byDTO(dto))
                    .isInstanceOf(RoleNotFoundException.class)
                    .hasMessage("Could not find role with id " + roleId);

            verifyNoInteractions(accountRoleMapper);
            verify(accountRoleRepo, never()).save(any());
        }

        @Test
        @DisplayName("EP - role name and account id -> account role is added")
        void roleNameAndAccountIdShouldAddAccountRole() {
            AccountEntity account = account();
            RoleEntity role = role();
            AccountRoleEntity savedEntity = new AccountRoleEntity();
            AccountRoleRDTO expectedResponse = response(role.getId());

            when(getRoleInstance.entityByName("ADMIN")).thenReturn(role);
            when(getAccountInstance.entityById(account.getId())).thenReturn(account);
            when(getRoleInstance.entityById(role.getId())).thenReturn(role);
            when(accountRoleRepo.existsByAccount_IdAndRole_Id(account.getId(), role.getId())).thenReturn(false);
            when(accountRoleRepo.save(anyAccountRoleEntity())).thenReturn(savedEntity);
            when(accountRoleMapper.entityToAccountRoleRDTO(savedEntity)).thenReturn(expectedResponse);

            AccountRoleRDTO response = addAccountRole.byRoleName("ADMIN", account.getId());

            assertThat(response).isSameAs(expectedResponse);
            verify(getRoleInstance).entityByName("ADMIN");
        }
    }

    private static AccountEntity account() {
        AccountEntity account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setEmail(MyEmail.of("rbac-account@example.com"));
        account.setPasswordHash("encoded-password");
        account.setDisplayName("RBAC Account");
        return account;
    }

    private static RoleEntity role() {
        RoleEntity role = new RoleEntity();
        role.setId(UUID.randomUUID());
        role.setName("ADMIN");
        role.setDescription("System administrator");
        return role;
    }

    private static AccountRoleRDTO response(UUID roleId) {
        return new AccountRoleRDTO(null, new RoleRDTO(roleId, "ADMIN", "System administrator"));
    }

    private static AccountRoleEntity anyAccountRoleEntity() {
        return org.mockito.ArgumentMatchers.any(AccountRoleEntity.class);
    }
}
