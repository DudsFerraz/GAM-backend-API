package br.org.gam.api.rbac.AccountRole.application.useCases.DropAccountRole;

import br.org.gam.api.rbac.AccountRole.application.AccountRoleDTO;
import br.org.gam.api.rbac.AccountRole.domain.AccountRole;
import java.util.UUID;

public interface DropAccountRole {
    void byDTO(AccountRoleDTO dto);
    void byRoleName(String roleName, UUID accountId);
}
