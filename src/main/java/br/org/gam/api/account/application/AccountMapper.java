package br.org.gam.api.account.application;

import br.org.gam.api.account.application.useCases.registerAccount.RegisterAccountRDTO;
import br.org.gam.api.account.domain.Account;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.rbac.accountRole.application.AccountRolesRDTO;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.role.domain.SystemRole;
import br.org.gam.api.rbac.role.application.RoleMapper;
import br.org.gam.api.rbac.role.application.RoleRDTO;
import br.org.gam.api.shared.auditing.IgnoreFullAuditFields;
import java.util.Collection;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring", uses = { RoleMapper.class })
public interface AccountMapper {

    // =====================================================================================
    // Domain <-> Persistence
    // =====================================================================================

    @IgnoreFullAuditFields
    AccountEntity domainToEntity(Account account);

    Account entityToDomain(AccountEntity accountEntity);

    // =====================================================================================
    // Persistence -> RDTO
    // =====================================================================================

    RegisterAccountRDTO entityToRegisterAccountRDTO(AccountEntity accountEntity);

    @Mapping(target = "roles", source = "accountRoles", qualifiedByName = "accountRolesToAccountRolesRDTO")
    AccountRDTO entityToRDTO(AccountEntity accountEntity);

    AccountSummaryRDTO entityToSummaryRDTO(AccountEntity accountEntity);

    @Mapping(source = "role", target = ".")
    RoleRDTO accountRoleToRoleRDTO(AccountRoleEntity accountRoleEntity);

    List<RoleRDTO> accountRolesToRoleRDTOs(Collection<AccountRoleEntity> accountRoles);

    // =====================================================================================
    // Helpers
    // =====================================================================================

    @Named("accountRolesToAccountRolesRDTO")
    default AccountRolesRDTO accountRolesToAccountRolesRDTO(Collection<AccountRoleEntity> accountRoles) {
        if (accountRoles == null) {
            return new AccountRolesRDTO();
        }
        List<AccountRoleEntity> currentRoleAssignments = accountRoles.stream()
                .filter(accountRole -> accountRole.getRole() != null)
                .filter(accountRole -> !accountRole.getRole().isSystemManaged()
                        || SystemRole.fromCode(accountRole.getRole().getName()).isPresent())
                .toList();
        return new AccountRolesRDTO(accountRolesToRoleRDTOs(currentRoleAssignments));
    }
}
