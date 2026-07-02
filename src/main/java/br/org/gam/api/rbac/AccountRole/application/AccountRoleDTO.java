package br.org.gam.api.rbac.AccountRole.application;

import java.util.UUID;

public record AccountRoleDTO(
        UUID accountId,
        UUID roleId
) {
}
