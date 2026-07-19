package br.org.gam.api.shared.activitylog;

import br.org.gam.api.shared.activitylog.events.AccountRoleAddedActivity;
import br.org.gam.api.shared.activitylog.events.AccountRoleRemovedActivity;
import br.org.gam.api.shared.activitylog.events.DeveloperMaintenanceActivity;
import br.org.gam.api.shared.activitylog.events.EventCreatedActivity;
import br.org.gam.api.shared.activitylog.events.MemberStatusChangedActivity;
import br.org.gam.api.shared.activitylog.events.MemberRegisteredActivity;
import br.org.gam.api.shared.activitylog.events.MembershipSolicitationActivity;
import java.util.HashMap;
import br.org.gam.api.shared.activitylog.events.MissaCreatedActivity;
import br.org.gam.api.shared.activitylog.events.OratorioCreatedActivity;
import br.org.gam.api.shared.activitylog.events.PresenceRegisteredActivity;
import br.org.gam.api.shared.activitylog.events.GamLocationCreatedActivity;
import br.org.gam.api.shared.activitylog.events.GamLocationRemovedActivity;
import br.org.gam.api.shared.activitylog.events.GamLocationUpdatedActivity;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ActivityLogEventListener {
    private final ActivityLogger activityLogger;

    public ActivityLogEventListener(ActivityLogger activityLogger) {
        this.activityLogger = activityLogger;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(MemberStatusChangedActivity activity) {
        Map<String, Object> metadata = new HashMap<>(Map.of(
                "memberId", activity.memberId(),
                "accountId", activity.accountId(),
                "previousStatus", activity.previousStatus(),
                "newStatus", activity.newStatus(),
                "roleAdded", activity.roleAdded(),
                "roleRemoved", activity.roleRemoved()
        ));
        if (activity.roleAddedId() != null) metadata.put("roleAddedId", activity.roleAddedId());
        if (activity.roleRemovedId() != null) metadata.put("roleRemovedId", activity.roleRemovedId());
        activityLogger.log(
                activity.action(),
                ActivityTargetType.MEMBER,
                activity.memberId(),
                activity.reason(),
                "Member status changed from " + activity.previousStatus() + " to " + activity.newStatus(),
                metadata
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(MemberRegisteredActivity activity) {
        activityLogger.log(
                ActivityAction.MEMBER_REGISTERED,
                ActivityTargetType.MEMBER,
                activity.memberId(),
                activity.reason(),
                "Member registered for account " + activity.accountId(),
                Map.of(
                        "memberId", activity.memberId(),
                        "accountId", activity.accountId(),
                        "newStatus", "ACTIVE",
                        "roleAddedId", activity.roleAddedId(),
                        "roleRemovedId", activity.roleRemovedId()
                )
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(MembershipSolicitationActivity activity) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("solicitationId", activity.solicitationId());
        metadata.put("applicantAccountId", activity.applicantAccountId());
        metadata.put("newStatus", activity.newStatus());
        if (activity.previousStatus() != null) metadata.put("previousStatus", activity.previousStatus());
        if (activity.memberId() != null) metadata.put("memberId", activity.memberId());
        if (activity.roleAddedId() != null) metadata.put("roleAddedId", activity.roleAddedId());
        if (activity.roleRemovedId() != null) metadata.put("roleRemovedId", activity.roleRemovedId());

        activityLogger.log(
                activity.action(),
                ActivityTargetType.MEMBERSHIP_SOLICITATION,
                activity.solicitationId(),
                activity.reason(),
                "Membership solicitation status is " + activity.newStatus(),
                metadata
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(AccountRoleAddedActivity activity) {
        activityLogger.log(
                ActivityAction.ACCOUNT_ROLE_ADDED,
                ActivityTargetType.ACCOUNT_ROLE,
                activity.accountRoleId(),
                activity.reason(),
                "Role " + activity.roleName() + " added to account " + activity.accountId(),
                Map.of(
                        "accountId", activity.accountId(),
                        "roleId", activity.roleId(),
                        "roleName", activity.roleName()
                )
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(AccountRoleRemovedActivity activity) {
        String roleName = activity.roleName() == null ? "" : activity.roleName();

        activityLogger.log(
                ActivityAction.ACCOUNT_ROLE_REMOVED,
                ActivityTargetType.ACCOUNT_ROLE,
                activity.accountRoleId(),
                activity.reason(),
                "Role " + activity.roleId() + " removed from account " + activity.accountId(),
                Map.of(
                        "accountId", activity.accountId(),
                        "roleId", activity.roleId(),
                        "roleName", roleName
                )
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(EventCreatedActivity activity) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("eventId", activity.eventId());
        metadata.put("eventType", activity.eventType().name());
        metadata.put("status", activity.status().name());
        metadata.put("gamLocationId", activity.gamLocationId());
        if (activity.requiredPermissionId() != null) {
            metadata.put("requiredPermissionId", activity.requiredPermissionId());
        }
        activityLogger.log(
                ActivityAction.EVENT_CREATED,
                ActivityTargetType.EVENT,
                activity.eventId(),
                null,
                "Event created: " + activity.title(),
                metadata
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(MissaCreatedActivity activity) {
        activityLogger.log(
                ActivityAction.MISSA_CREATED,
                ActivityTargetType.MISSA,
                activity.missaId(),
                null,
                "Missa created for event " + activity.eventId(),
                Map.of(
                        "missaId", activity.missaId(),
                        "eventId", activity.eventId()
                )
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(OratorioCreatedActivity activity) {
        activityLogger.log(
                ActivityAction.ORATORIO_CREATED,
                ActivityTargetType.ORATORIO,
                activity.oratorioId(),
                null,
                "Oratorio created for event " + activity.eventId(),
                Map.of(
                        "oratorioId", activity.oratorioId(),
                        "eventId", activity.eventId()
                )
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(PresenceRegisteredActivity activity) {
        activityLogger.log(
                ActivityAction.PRESENCE_REGISTERED,
                ActivityTargetType.PRESENCE,
                activity.presenceId(),
                null,
                "Presence registered for member " + activity.memberId() + " and event " + activity.eventId(),
                Map.of(
                        "presenceId", activity.presenceId(),
                        "memberId", activity.memberId(),
                        "eventId", activity.eventId()
                )
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(GamLocationCreatedActivity activity) {
        activityLogger.log(
                ActivityAction.GAM_LOCATION_CREATED,
                ActivityTargetType.GAM_LOCATION,
                activity.locationId(),
                null,
                "GamLocation created",
                Map.of()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(GamLocationUpdatedActivity activity) {
        activityLogger.log(
                ActivityAction.GAM_LOCATION_UPDATED,
                ActivityTargetType.GAM_LOCATION,
                activity.locationId(),
                null,
                "GamLocation updated",
                Map.of("changedFields", activity.changedFields())
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(GamLocationRemovedActivity activity) {
        activityLogger.log(
                ActivityAction.GAM_LOCATION_REMOVED,
                ActivityTargetType.GAM_LOCATION,
                activity.locationId(),
                activity.reason(),
                "GamLocation removed",
                Map.of("name", activity.name())
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(DeveloperMaintenanceActivity activity) {
        activityLogger.log(
                activity.action(),
                ActivityTargetType.MAINTENANCE_RECORD,
                activity.targetId(),
                activity.reason(),
                activity.summary(),
                activity.metadata()
        );
    }
}
