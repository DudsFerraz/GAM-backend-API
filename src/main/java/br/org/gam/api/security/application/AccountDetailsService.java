package br.org.gam.api.security.application;

import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.account.persistence.AccountRepository;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.rbac.permission.persistence.PermissionEntity;
import br.org.gam.api.rbac.role.domain.SystemRole;
import br.org.gam.api.rbac.role.persistence.RoleEntity;
import br.org.gam.api.rbac.rolePermission.persistence.RolePermissionEntity;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountDetailsService implements UserDetailsService {

    private final AccountRepository accountRepo;

    public AccountDetailsService(@Lazy AccountRepository accountRepo) {
        this.accountRepo = accountRepo;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String subject) throws UsernameNotFoundException {

        AccountEntity accountEntity;
        try {
            UUID id = UUID.fromString(subject);
            accountEntity = accountRepo.findById(id)
                    .orElseThrow(() -> new UsernameNotFoundException("User ID not found: " + subject));
        } catch (IllegalArgumentException e) {
            GamEmail email;
            try {
                email = GamEmail.of(subject);
            } catch (IllegalArgumentException invalidEmail) {
                throw new UsernameNotFoundException("User subject not found: " + subject, invalidEmail);
            }

            accountEntity = accountRepo.findByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("User Email not found: " + subject));
        }

        Set<GrantedAuthority> authorities = new HashSet<>();

        if (accountEntity.getAccountRoles() != null) {
            for (AccountRoleEntity accountRole : accountEntity.getAccountRoles()) {
                RoleEntity role = accountRole.getRole();
                if (role == null) continue;

                SystemRole systemRole = null;
                if (role.isSystemManaged()) {
                    systemRole = SystemRole.fromCode(role.getName()).orElse(null);
                    if (systemRole == null) continue;
                }

                if (role.getRolePermissions() != null) {
                    for (RolePermissionEntity rolePermission : role.getRolePermissions()) {
                        PermissionEntity permission = rolePermission.getPermission();
                        if (permission == null || !permission.isSystemManaged()) continue;

                        PermissionEnum currentPermission = PermissionEnum.fromCode(permission.getCode()).orElse(null);
                        if (currentPermission == null) continue;
                        if (systemRole != null && !systemRole.includes(currentPermission)) continue;

                        authorities.add(new SimpleGrantedAuthority(currentPermission.getCode()));
                    }
                }
            }
        }

        return new AccountDetails(accountEntity, authorities);
    }
}
