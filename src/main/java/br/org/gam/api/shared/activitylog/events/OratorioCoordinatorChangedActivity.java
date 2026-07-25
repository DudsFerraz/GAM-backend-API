package br.org.gam.api.shared.activitylog.events;

import br.org.gam.api.shared.activitylog.ActivityAction;
import java.util.UUID;

public record OratorioCoordinatorChangedActivity(
        ActivityAction action,
        UUID memberId,
        UUID accountId,
        UUID roleId,
        String roleCode,
        String reason
) {
}
