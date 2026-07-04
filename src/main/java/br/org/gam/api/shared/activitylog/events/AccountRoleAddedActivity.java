package br.org.gam.api.shared.activitylog.events;

import java.util.UUID;

public record AccountRoleAddedActivity(
        UUID accountRoleId,
        UUID accountId,
        UUID roleId,
        String roleName,
        String reason
) {
}
