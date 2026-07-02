package br.org.gam.api.rbac.AccountRole.application.useCases.GetAccountRoleInstance;

import br.org.gam.api.rbac.AccountRole.application.AccountRoleDTO;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleEntity;

public interface GetAccountRoleInstance {
    AccountRoleEntity entityByDTO(AccountRoleDTO dto);
}
