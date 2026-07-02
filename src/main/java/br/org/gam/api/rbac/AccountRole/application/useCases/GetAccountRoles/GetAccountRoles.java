package br.org.gam.api.rbac.AccountRole.application.useCases.GetAccountRoles;

import br.org.gam.api.rbac.AccountRole.application.AccountRolesRDTO;
import java.util.UUID;

public interface GetAccountRoles {
    AccountRolesRDTO get(UUID accountId);
}
