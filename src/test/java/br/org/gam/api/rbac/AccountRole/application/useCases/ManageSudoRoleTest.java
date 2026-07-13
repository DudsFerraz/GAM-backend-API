package br.org.gam.api.rbac.accountRole.application.useCases;

import br.org.gam.api.account.application.AccountEntityLoader;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.rbac.accountRole.application.AccountRoleDTO;
import br.org.gam.api.rbac.accountRole.application.AccountRoleEntityLoader;
import br.org.gam.api.rbac.accountRole.application.AccountRoleMapper;
import br.org.gam.api.rbac.accountRole.application.AccountRoleRDTO;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleRepository;
import br.org.gam.api.rbac.role.application.RoleEntityLoader;
import br.org.gam.api.rbac.role.application.RoleRDTO;
import br.org.gam.api.rbac.role.domain.SystemRole;
import br.org.gam.api.rbac.role.persistence.RoleEntity;
import br.org.gam.api.rbac.application.RbacSafetyPolicy;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.shared.exception.ConflictException;
import br.org.gam.api.shared.exception.ForbiddenOperationException;
import br.org.gam.api.shared.exception.InvalidCommandException;
import br.org.gam.api.shared.exception.NotFoundException;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Manage SUDO Role Use Case")
class ManageSudoRoleTest {

    @Mock
    private AccountEntityLoader accountEntityLoader;

    @Mock
    private RoleEntityLoader roleEntityLoader;

    @Mock
    private AccountRoleEntityLoader accountRoleEntityLoader;

    @Mock
    private AccountRoleRepository accountRoleRepo;

    @Mock
    private AccountRoleMapper accountRoleMapper;

    @Mock
    private ActivityEvents activityEvents;

    @Mock
    private RbacSafetyPolicy rbacSafetyPolicy;

    @InjectMocks
    private ManageSudoRole manageSudoRole;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("REQ-ACCOUNT-ROLE-009 and REQ-ACCOUNT-ROLE-011 - active Account without SUDO -> assignment and one audit event")
        void activeAccountWithoutSudoShouldCreateAssignmentAndAudit() {
            UUID accountId = UUID.randomUUID();
            AccountEntity account = account(accountId);
            RoleEntity sudoRole = role(SystemRole.SUDO.getCode());
            AccountRoleEntity savedAssignment = accountRole(account, sudoRole);
            AccountRoleRDTO expectedResponse = new AccountRoleRDTO(
                    null,
                    null,
                    new RoleRDTO(sudoRole.getId(), sudoRole.getName(), sudoRole.getDescription(), true)
            );
            String reason = "Grant recovery access";

            when(accountEntityLoader.requiredById(accountId)).thenReturn(account);
            when(roleEntityLoader.requiredByName(SystemRole.SUDO.getCode())).thenReturn(sudoRole);
            when(accountRoleRepo.existsByAccount_IdAndRole_Id(accountId, sudoRole.getId())).thenReturn(false);
            when(accountRoleRepo.save(any(AccountRoleEntity.class))).thenReturn(savedAssignment);
            when(accountRoleMapper.entityToRDTO(savedAssignment)).thenReturn(expectedResponse);

            assertThat(manageSudoRole.assignSudo(accountId, " " + reason + " "))
                    .isSameAs(expectedResponse);

            ArgumentCaptor<AccountRoleEntity> assignmentCaptor = ArgumentCaptor.forClass(AccountRoleEntity.class);
            verify(accountRoleRepo).save(assignmentCaptor.capture());
            assertThat(assignmentCaptor.getValue().getId()).isNotNull();
            assertThat(assignmentCaptor.getValue().getAccount()).isSameAs(account);
            assertThat(assignmentCaptor.getValue().getRole()).isSameAs(sudoRole);
            verify(activityEvents).accountRoleAdded(
                    savedAssignment.getId(), accountId, sudoRole.getId(), SystemRole.SUDO.getCode(), reason);
        }

        @Test
        @DisplayName("REQ-ACCOUNT-ROLE-011 - duplicate active SUDO assignment -> conflict without mutation or audit")
        void duplicateActiveSudoAssignmentShouldReturnConflictWithoutMutationOrAudit() {
            UUID accountId = UUID.randomUUID();
            AccountEntity account = account(accountId);
            RoleEntity sudoRole = role(SystemRole.SUDO.getCode());

            when(accountEntityLoader.requiredById(accountId)).thenReturn(account);
            when(roleEntityLoader.requiredByName(SystemRole.SUDO.getCode())).thenReturn(sudoRole);
            when(accountRoleRepo.existsByAccount_IdAndRole_Id(accountId, sudoRole.getId())).thenReturn(true);

            assertThatThrownBy(() -> manageSudoRole.assignSudo(accountId, "Grant duplicate recovery access"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Account: " + account.getEmail() + " already has role: " + sudoRole.getName());

            verify(accountRoleRepo, never()).save(any(AccountRoleEntity.class));
            verifyNoInteractions(accountRoleMapper, activityEvents, rbacSafetyPolicy);
        }

        @ParameterizedTest
        @MethodSource("invalidReasons")
        @DisplayName("REQ-ACCOUNT-ROLE-005 - invalid maintenance reason -> rejected before loading or mutation")
        void invalidMaintenanceReasonShouldBeRejectedBeforeLoadingOrMutation(String reason) {
            UUID accountId = UUID.randomUUID();

            assertThatThrownBy(() -> manageSudoRole.assignSudo(accountId, reason))
                    .isInstanceOf(InvalidCommandException.class);
            assertThatThrownBy(() -> manageSudoRole.removeSudo(accountId, reason))
                    .isInstanceOf(InvalidCommandException.class);

            verifyNoInteractions(
                    accountEntityLoader,
                    roleEntityLoader,
                    accountRoleEntityLoader,
                    accountRoleRepo,
                    accountRoleMapper,
                    activityEvents,
                    rbacSafetyPolicy
            );
        }

        @Test
        @DisplayName("REQ-ACCOUNT-ROLE-010 and REQ-ACCOUNT-ROLE-011 - missing active Account -> not found without mutation")
        void missingActiveAccountShouldReturnNotFoundWithoutMutation() {
            UUID accountId = UUID.randomUUID();
            when(accountEntityLoader.requiredById(accountId))
                    .thenThrow(NotFoundException.resource("Account", accountId));

            assertThatThrownBy(() -> manageSudoRole.assignSudo(accountId, "Grant recovery access"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Account not found with identifier " + accountId);

            verifyNoInteractions(roleEntityLoader, accountRoleRepo, accountRoleMapper, activityEvents);
        }

        @Test
        @DisplayName("REQ-ACCOUNT-ROLE-012 - missing active SUDO assignment -> not found without mutation or audit")
        void missingActiveSudoAssignmentShouldReturnNotFoundWithoutMutationOrAudit() {
            UUID accountId = UUID.randomUUID();
            RoleEntity sudoRole = role(SystemRole.SUDO.getCode());
            AccountRoleDTO dto = new AccountRoleDTO(accountId, sudoRole.getId(), "Remove missing recovery access");

            when(roleEntityLoader.requiredByName(SystemRole.SUDO.getCode())).thenReturn(sudoRole);
            when(accountRoleEntityLoader.requiredByDTO(dto))
                    .thenThrow(NotFoundException.resource("AccountRole", accountId + ":" + sudoRole.getId()));

            assertThatThrownBy(() -> manageSudoRole.removeSudo(accountId, "Remove missing recovery access"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("AccountRole not found with identifier " + accountId + ":" + sudoRole.getId());

            verifyNoInteractions(rbacSafetyPolicy, accountRoleRepo, activityEvents);
        }

        @Test
        @DisplayName("REQ-ACCOUNT-ROLE-012 - active SUDO assignment with another SUDO Account -> soft-delete and one audit event")
        void activeSudoAssignmentWithAnotherSudoAccountShouldBeRemovedAndAudited() {
            UUID accountId = UUID.randomUUID();
            RoleEntity sudoRole = role(SystemRole.SUDO.getCode());
            AccountRoleEntity assignment = accountRole(account(accountId), sudoRole);
            String reason = "Remove retired recovery access";

            when(roleEntityLoader.requiredByName(SystemRole.SUDO.getCode())).thenReturn(sudoRole);
            when(accountRoleEntityLoader.requiredByDTO(any(AccountRoleDTO.class))).thenReturn(assignment);

            manageSudoRole.removeSudo(accountId, " " + reason + " ");

            verify(rbacSafetyPolicy).assertCanRemoveSudoThroughInternalService(assignment);
            verify(accountRoleRepo).delete(assignment);
            verify(activityEvents).accountRoleRemoved(
                    assignment.getId(), accountId, sudoRole.getId(), SystemRole.SUDO.getCode(), reason);
        }

        @Test
        @DisplayName("REQ-ACCOUNT-ROLE-013 - last active SUDO assignment -> forbidden without mutation or audit")
        void lastActiveSudoAssignmentShouldBeForbiddenWithoutMutationOrAudit() {
            UUID accountId = UUID.randomUUID();
            RoleEntity sudoRole = role(SystemRole.SUDO.getCode());
            AccountRoleEntity assignment = accountRole(account(accountId), sudoRole);

            when(roleEntityLoader.requiredByName(SystemRole.SUDO.getCode())).thenReturn(sudoRole);
            when(accountRoleEntityLoader.requiredByDTO(any(AccountRoleDTO.class))).thenReturn(assignment);
            doThrow(ForbiddenOperationException.reason("Cannot remove the last active SUDO account."))
                    .when(rbacSafetyPolicy)
                    .assertCanRemoveSudoThroughInternalService(assignment);

            assertThatThrownBy(() -> manageSudoRole.removeSudo(accountId, "Remove final recovery access"))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("Cannot remove the last active SUDO account.");

            verify(accountRoleRepo, never()).delete(any(AccountRoleEntity.class));
            verifyNoInteractions(activityEvents);
        }

        private static Stream<String> invalidReasons() {
            return Stream.of(null, "", " \n\t", "a".repeat(2_001));
        }
    }

    private static AccountEntity account(UUID accountId) {
        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setEmail(GamEmail.of(accountId + "@example.com"));
        account.setPasswordHash("encoded-password");
        account.setDisplayName("SUDO Account");
        return account;
    }

    private static RoleEntity role(String name) {
        RoleEntity role = new RoleEntity();
        role.setId(UUID.randomUUID());
        role.setName(name);
        role.setDescription(name + " role");
        role.setSystemManaged(true);
        return role;
    }

    private static AccountRoleEntity accountRole(AccountEntity account, RoleEntity role) {
        AccountRoleEntity assignment = new AccountRoleEntity();
        assignment.setId(UUID.randomUUID());
        assignment.setAccount(account);
        assignment.setRole(role);
        return assignment;
    }
}
