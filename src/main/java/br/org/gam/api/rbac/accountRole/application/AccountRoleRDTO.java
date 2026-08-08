package br.org.gam.api.rbac.accountRole.application;

import br.org.gam.api.account.application.AccountRDTO;
import br.org.gam.api.rbac.role.application.RoleRDTO;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record AccountRoleRDTO(
        @JsonProperty("id") UUID assignmentId,
        AccountRDTO account,
        RoleRDTO role
) {
    public AccountRoleRDTO(AccountRDTO account, RoleRDTO role) {
        this(null, account, role);
    }
}
