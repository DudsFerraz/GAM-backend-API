package br.org.gam.api.rbac.AccountRole.application;

import br.org.gam.api.rbac.AccountRole.domain.AccountRole;
import java.util.UUID;

public record AccountRoleDTO(
        UUID accountId,
        UUID roleId
) {
}
