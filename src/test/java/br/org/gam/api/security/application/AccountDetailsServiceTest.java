package br.org.gam.api.security.application;

import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.account.persistence.AccountRepository;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.rbac.permission.persistence.PermissionEntity;
import br.org.gam.api.rbac.role.persistence.RoleEntity;
import br.org.gam.api.rbac.rolePermission.persistence.RolePermissionEntity;
import br.org.gam.api.testing.annotation.SecurityTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Account Details Service")
class AccountDetailsServiceTest {

    @Mock
    private AccountRepository accountRepo;

    @Nested
    @SecurityTest
    @DisplayName("Security")
    class Security {

        @Test
        @DisplayName("account roles -> permission authorities only")
        void accountRolesShouldReturnPermissionAuthoritiesOnly() {
            AccountDetailsService service = new AccountDetailsService(accountRepo);
            UUID accountId = UUID.randomUUID();
            AccountEntity account = account(accountId);
            RoleEntity role = role("MEMBER");
            PermissionEntity permission = permission(PermissionEnum.MEMBER_GET);
            role.setRolePermissions(Set.of(rolePermission(role, permission)));
            account.setAccountRoles(Set.of(accountRole(account, role)));

            when(accountRepo.findById(accountId)).thenReturn(Optional.of(account));

            UserDetails userDetails = service.loadUserByUsername(accountId.toString());

            assertThat(userDetails.getAuthorities())
                    .extracting("authority")
                    .containsExactly(PermissionEnum.MEMBER_GET.getCode())
                    .doesNotContain("ROLE_MEMBER");
        }

        @Test
        @DisplayName("invalid subject -> username-not-found error")
        void invalidSubjectShouldReturnUsernameNotFoundError() {
            AccountDetailsService service = new AccountDetailsService(accountRepo);

            assertThatThrownBy(() -> service.loadUserByUsername("not-a-valid-subject"))
                    .isInstanceOf(UsernameNotFoundException.class);
        }
    }

    private static AccountEntity account(UUID accountId) {
        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setEmail(GamEmail.of(accountId + "@example.com"));
        account.setPasswordHash("encoded-password");
        account.setDisplayName("Account");
        return account;
    }

    private static RoleEntity role(String name) {
        RoleEntity role = new RoleEntity();
        role.setId(UUID.randomUUID());
        role.setName(name);
        role.setDescription(name + " role");
        return role;
    }

    private static PermissionEntity permission(PermissionEnum permissionEnum) {
        PermissionEntity permission = new PermissionEntity();
        permission.setId(UUID.randomUUID());
        permission.setCode(permissionEnum.getCode());
        permission.setLabel(permissionEnum.getLabel());
        permission.setDescription(permissionEnum.getDescription());
        permission.setSystemManaged(true);
        return permission;
    }

    private static RolePermissionEntity rolePermission(RoleEntity role, PermissionEntity permission) {
        RolePermissionEntity rolePermission = new RolePermissionEntity();
        rolePermission.setId(UUID.randomUUID());
        rolePermission.setRole(role);
        rolePermission.setPermission(permission);
        return rolePermission;
    }

    private static AccountRoleEntity accountRole(AccountEntity account, RoleEntity role) {
        AccountRoleEntity accountRole = new AccountRoleEntity();
        accountRole.setId(UUID.randomUUID());
        accountRole.setAccount(account);
        accountRole.setRole(role);
        return accountRole;
    }
}
