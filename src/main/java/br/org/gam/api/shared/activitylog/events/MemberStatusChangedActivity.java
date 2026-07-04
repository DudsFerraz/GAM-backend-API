package br.org.gam.api.shared.activitylog.events;

import br.org.gam.api.shared.activitylog.ActivityAction;
import java.util.UUID;

public record MemberStatusChangedActivity(
        ActivityAction action,
        UUID memberId,
        UUID accountId,
        String previousStatus,
        String newStatus,
        String roleAdded,
        String roleRemoved,
        String reason
) {
}
