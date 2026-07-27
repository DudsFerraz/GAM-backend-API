package br.org.gam.api.shared.activitylog.events;

import br.org.gam.api.shared.activitylog.ActivityActorKind;
import java.util.UUID;

public record AccountRoleRemovedActivity(
        UUID accountRoleId,
        UUID accountId,
        UUID roleId,
        String roleName,
        String reason,
        ActivityActorKind actorKind
) {
    public AccountRoleRemovedActivity(
            UUID accountRoleId,
            UUID accountId,
            UUID roleId,
            String roleName,
            String reason
    ) {
        this(accountRoleId, accountId, roleId, roleName, reason, ActivityActorKind.ACCOUNT);
    }
}
