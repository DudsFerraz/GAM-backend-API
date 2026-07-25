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
        UUID roleAddedId,
        UUID roleRemovedId,
        UUID additionallyRemovedRoleId,
        UUID secondAdditionallyRemovedRoleId,
        String reason
) {
    public MemberStatusChangedActivity(ActivityAction action, UUID memberId, UUID accountId,
                                       String previousStatus, String newStatus, String roleAdded,
                                       String roleRemoved, UUID roleAddedId, UUID roleRemovedId, String reason) {
        this(action, memberId, accountId, previousStatus, newStatus, roleAdded, roleRemoved,
                roleAddedId, roleRemovedId, null, null, reason);
    }

    public MemberStatusChangedActivity(ActivityAction action, UUID memberId, UUID accountId,
                                       String previousStatus, String newStatus, String roleAdded,
                                       String roleRemoved, String reason) {
        this(action, memberId, accountId, previousStatus, newStatus, roleAdded, roleRemoved,
                null, null, null, null, reason);
    }
}
