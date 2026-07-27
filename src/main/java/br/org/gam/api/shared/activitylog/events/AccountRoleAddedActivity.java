package br.org.gam.api.shared.activitylog.events;

import br.org.gam.api.shared.activitylog.ActivityActorKind;
import java.util.UUID;

public record AccountRoleAddedActivity(
        UUID accountRoleId,
        UUID accountId,
        UUID roleId,
        String roleName,
        String reason,
        ActivityActorKind actorKind
) {
    public AccountRoleAddedActivity(
            UUID accountRoleId,
            UUID accountId,
            UUID roleId,
            String roleName,
            String reason
    ) {
        this(accountRoleId, accountId, roleId, roleName, reason, ActivityActorKind.ACCOUNT);
    }
}
