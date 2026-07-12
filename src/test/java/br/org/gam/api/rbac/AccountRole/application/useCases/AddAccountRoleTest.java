package br.org.gam.api.rbac.accountRole.application.useCases;

import br.org.gam.api.account.application.AccountEntityLoader;
import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.rbac.accountRole.application.AccountRoleDTO;
import br.org.gam.api.rbac.accountRole.application.AccountRoleMapper;
import br.org.gam.api.rbac.accountRole.application.AccountRoleRDTO;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleRepository;
import br.org.gam.api.rbac.role.application.RoleRDTO;
import br.org.gam.api.rbac.role.application.RoleEntityLoader;
import br.org.gam.api.rbac.role.persistence.RoleEntity;
import br.org.gam.api.rbac.application.RbacSafetyPolicy;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.exception.ConflictException;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import java.util.stream.Stream;

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
    private AccountEntityLoader getAccountInstance;

    @Mock
    private RoleEntityLoader getRoleInstance;

    @Mock
    private AccountRoleMapper accountRoleMapper;

    @Mock
    private ActivityEvents activityEvents;

    @Mock
    private RbacSafetyPolicy rbacSafetyPolicy;

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
            AccountRoleDTO dto = new AccountRoleDTO(account.getId(), role.getId(), "Grant admin access");
            AccountRoleEntity savedEntity = new AccountRoleEntity();
            savedEntity.setId(UUID.randomUUID());
            AccountRoleRDTO expectedResponse = response(role.getId());

            when(getAccountInstance.requiredById(dto.accountId())).thenReturn(account);
            when(getRoleInstance.requiredById(dto.roleId())).thenReturn(role);
            when(accountRoleRepo.existsByAccount_IdAndRole_Id(account.getId(), role.getId())).thenReturn(false);
            when(accountRoleRepo.save(anyAccountRoleEntity())).thenReturn(savedEntity);
            when(accountRoleMapper.entityToRDTO(savedEntity)).thenReturn(expectedResponse);

            AccountRoleRDTO response = addAccountRole.byDTO(dto);

            assertThat(response).isSameAs(expectedResponse);

            ArgumentCaptor<AccountRoleEntity> accountRoleCaptor = ArgumentCaptor.forClass(AccountRoleEntity.class);
            verify(accountRoleRepo).save(accountRoleCaptor.capture());
            AccountRoleEntity accountRole = accountRoleCaptor.getValue();
            assertThat(accountRole.getId()).isNotNull();
            assertThat(accountRole.getId().version()).isEqualTo(7);
            assertThat(accountRole.getAccount()).isSameAs(account);
            assertThat(accountRole.getRole()).isSameAs(role);
            verify(rbacSafetyPolicy).assertCanAssignRoleThroughAdmin(role);
            verify(activityEvents).accountRoleAdded(
                    savedEntity.getId(), account.getId(), role.getId(), role.getName(), "Grant admin access");
        }

        @Test
        @DisplayName("EP - account role add without reason -> validation error")
        void accountRoleAddWithoutReasonShouldReturnValidationError() {
            AccountRoleDTO dto = new AccountRoleDTO(UUID.randomUUID(), UUID.randomUUID(), " ");

            assertThatThrownBy(() -> addAccountRole.byDTO(dto))
                    .isInstanceOf(InvalidCommandException.class)
                    .hasMessage("Account role changes require an audit reason.");

            verifyNoInteractions(getAccountInstance, getRoleInstance, accountRoleMapper, activityEvents, rbacSafetyPolicy);
            verify(accountRoleRepo, never()).save(any());
        }

        @ParameterizedTest
        @MethodSource("invalidReasons")
        @DisplayName("REQ-ACCOUNT-ROLE-005 - invalid reason -> rejected before loading or mutation")
        void invalidReasonShouldBeRejectedBeforeLoadingOrMutation(String reason) {
            AccountRoleDTO dto = new AccountRoleDTO(UUID.randomUUID(), UUID.randomUUID(), reason);

            assertThatThrownBy(() -> addAccountRole.byDTO(dto))
                    .isInstanceOf(InvalidCommandException.class);

            verifyNoInteractions(
                    accountRoleRepo,
                    getAccountInstance,
                    getRoleInstance,
                    accountRoleMapper,
                    activityEvents,
                    rbacSafetyPolicy
            );
        }

        @Test
        @DisplayName("REQ-ACCOUNT-ROLE-005 - maximum reason length -> accepted after trimming")
        void maximumReasonLengthShouldBeAcceptedAfterTrimming() {
            AccountEntity account = account();
            RoleEntity role = role();
            String reason = "a".repeat(2_000);
            AccountRoleDTO dto = new AccountRoleDTO(
                    account.getId(),
                    role.getId(),
                    " " + reason + " "
            );
            AccountRoleEntity savedEntity = new AccountRoleEntity();
            savedEntity.setId(UUID.randomUUID());
            AccountRoleRDTO expectedResponse = response(role.getId());

            when(getAccountInstance.requiredById(account.getId())).thenReturn(account);
            when(getRoleInstance.requiredById(role.getId())).thenReturn(role);
            when(accountRoleRepo.existsByAccount_IdAndRole_Id(account.getId(), role.getId())).thenReturn(false);
            when(accountRoleRepo.save(anyAccountRoleEntity())).thenReturn(savedEntity);
            when(accountRoleMapper.entityToRDTO(savedEntity)).thenReturn(expectedResponse);

            assertThat(addAccountRole.byDTO(dto)).isSameAs(expectedResponse);

            verify(activityEvents).accountRoleAdded(
                    savedEntity.getId(), account.getId(), role.getId(), role.getName(), reason);
        }

        @Test
        @DisplayName("REQ-ACCOUNT-ROLE-007 - non-direct workflow add -> no Account-role audit event")
        void nonDirectWorkflowAddShouldNotPublishAccountRoleAuditEvent() {
            AccountEntity account = account();
            RoleEntity role = role();
            AccountRoleDTO dto = new AccountRoleDTO(account.getId(), role.getId(), null);
            AccountRoleEntity savedEntity = new AccountRoleEntity();
            savedEntity.setId(UUID.randomUUID());
            AccountRoleRDTO expectedResponse = response(role.getId());

            when(getAccountInstance.requiredById(account.getId())).thenReturn(account);
            when(getRoleInstance.requiredById(role.getId())).thenReturn(role);
            when(accountRoleRepo.existsByAccount_IdAndRole_Id(account.getId(), role.getId())).thenReturn(false);
            when(accountRoleRepo.save(anyAccountRoleEntity())).thenReturn(savedEntity);
            when(accountRoleMapper.entityToRDTO(savedEntity)).thenReturn(expectedResponse);

            addAccountRole.byDTO(dto, false);

            verifyNoInteractions(activityEvents);
        }

        @Test
        @DisplayName("EP - account already has role -> conflict error")
        void accountAlreadyHasRoleShouldReturnConflictError() {
            AccountEntity account = account();
            RoleEntity role = role();
            AccountRoleDTO dto = new AccountRoleDTO(account.getId(), role.getId(), "Grant duplicate access");

            when(getAccountInstance.requiredById(dto.accountId())).thenReturn(account);
            when(getRoleInstance.requiredById(dto.roleId())).thenReturn(role);
            when(accountRoleRepo.existsByAccount_IdAndRole_Id(account.getId(), role.getId())).thenReturn(true);

            assertThatThrownBy(() -> addAccountRole.byDTO(dto))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Account: " + account.getEmail() + " already has role: " + role.getName());

            verifyNoInteractions(accountRoleMapper);
            verify(accountRoleRepo, never()).save(any());
        }

        @Test
        @DisplayName("EP - missing account id -> not found error")
        void missingAccountIdShouldReturnNotFoundError() {
            UUID accountId = UUID.randomUUID();
            UUID roleId = UUID.randomUUID();
            AccountRoleDTO dto = new AccountRoleDTO(accountId, roleId, "Grant missing account access");

            when(getAccountInstance.requiredById(accountId))
                    .thenThrow(NotFoundException.resource("Account", accountId));

            assertThatThrownBy(() -> addAccountRole.byDTO(dto))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Account not found with identifier " + accountId);

            verifyNoInteractions(getRoleInstance, accountRoleMapper);
            verify(accountRoleRepo, never()).save(any());
        }

        @Test
        @DisplayName("EP - missing role id -> not found error")
        void missingRoleIdShouldReturnNotFoundError() {
            AccountEntity account = account();
            UUID roleId = UUID.randomUUID();
            AccountRoleDTO dto = new AccountRoleDTO(account.getId(), roleId, "Grant missing role access");

            when(getAccountInstance.requiredById(account.getId())).thenReturn(account);
            when(getRoleInstance.requiredById(roleId))
                    .thenThrow(NotFoundException.resource("Role", roleId));

            assertThatThrownBy(() -> addAccountRole.byDTO(dto))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Role not found with identifier " + roleId);

            verifyNoInteractions(accountRoleMapper);
            verify(accountRoleRepo, never()).save(any());
        }

        @Test
        @DisplayName("EP - role name and account id -> account role is added")
        void roleNameAndAccountIdShouldAddAccountRole() {
            AccountEntity account = account();
            RoleEntity role = role();
            AccountRoleEntity savedEntity = new AccountRoleEntity();
            savedEntity.setId(UUID.randomUUID());
            AccountRoleRDTO expectedResponse = response(role.getId());

            when(getRoleInstance.requiredByName("ADMIN")).thenReturn(role);
            when(getAccountInstance.requiredById(account.getId())).thenReturn(account);
            when(getRoleInstance.requiredById(role.getId())).thenReturn(role);
            when(accountRoleRepo.existsByAccount_IdAndRole_Id(account.getId(), role.getId())).thenReturn(false);
            when(accountRoleRepo.save(anyAccountRoleEntity())).thenReturn(savedEntity);
            when(accountRoleMapper.entityToRDTO(savedEntity)).thenReturn(expectedResponse);

            AccountRoleRDTO response = addAccountRole.byRoleName("ADMIN", account.getId(), "Grant admin access");

            assertThat(response).isSameAs(expectedResponse);
            verify(getRoleInstance).requiredByName("ADMIN");
            verify(activityEvents).accountRoleAdded(
                    savedEntity.getId(), account.getId(), role.getId(), role.getName(), "Grant admin access");
        }

        private static Stream<String> invalidReasons() {
            return Stream.of(null, "", " \n\t", "a".repeat(2_001));
        }
    }

    private static AccountEntity account() {
        AccountEntity account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setEmail(GamEmail.of("rbac-account@example.com"));
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
