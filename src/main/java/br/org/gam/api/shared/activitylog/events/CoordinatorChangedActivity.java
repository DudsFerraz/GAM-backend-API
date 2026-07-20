package br.org.gam.api.shared.activitylog.events;

import br.org.gam.api.shared.activitylog.ActivityAction;
import java.util.UUID;

public record CoordinatorChangedActivity(
        ActivityAction action,
        UUID memberId,
        UUID accountId,
        UUID coordRoleId,
        String reason
) {
}
