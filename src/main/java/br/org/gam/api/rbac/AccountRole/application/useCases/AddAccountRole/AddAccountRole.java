package br.org.gam.api.rbac.AccountRole.application.useCases.AddAccountRole;

import br.org.gam.api.rbac.AccountRole.application.AccountRoleDTO;
import br.org.gam.api.rbac.AccountRole.application.AccountRoleRDTO;
import java.util.UUID;

public interface AddAccountRole {
    AccountRoleRDTO byDTO(AccountRoleDTO dto);
    AccountRoleRDTO byRoleName(String roleName, UUID accountId);
}
