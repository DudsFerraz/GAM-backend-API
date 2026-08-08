package br.org.gam.api.rbac.accountRole.application;

import java.util.UUID;

public record AccountRoleDTO(
        UUID accountId,
        UUID roleId,
        String reason
) {
    public AccountRoleDTO(UUID accountId, UUID roleId) {
        this(accountId, roleId, null);
    }
}
