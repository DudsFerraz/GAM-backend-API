package br.org.gam.api.rbac.AccountRole.application;

import br.org.gam.api.account.application.AccountRDTO;
import br.org.gam.api.rbac.AccountRole.domain.AccountRole;
import br.org.gam.api.rbac.Role.application.RoleRDTO;

public record AccountRoleRDTO(
        AccountRDTO account,
        RoleRDTO role
) {
}
