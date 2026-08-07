package br.org.gam.api.shared.activitylog;

import br.org.gam.api.shared.activitylog.events.AccountRoleAddedActivity;
import br.org.gam.api.shared.activitylog.events.AccountRoleRemovedActivity;
import br.org.gam.api.shared.activitylog.events.AccountRegisteredActivity;
import br.org.gam.api.shared.activitylog.events.DeveloperMaintenanceActivity;
import br.org.gam.api.shared.activitylog.events.EventCreatedActivity;
import br.org.gam.api.shared.activitylog.events.EventChangedActivity;
import br.org.gam.api.shared.activitylog.events.MemberStatusChangedActivity;
import br.org.gam.api.shared.activitylog.events.CoordinatorChangedActivity;
import br.org.gam.api.shared.activitylog.events.OratorioCoordinatorChangedActivity;
import br.org.gam.api.shared.activitylog.events.ModuleActivity;
import br.org.gam.api.shared.activitylog.events.MemberRegisteredActivity;
import br.org.gam.api.shared.activitylog.events.MembershipSolicitationActivity;
import java.util.HashMap;
import br.org.gam.api.shared.activitylog.events.OratorioCreatedActivity;
import br.org.gam.api.shared.activitylog.events.PresenceRegisteredActivity;
import br.org.gam.api.shared.activitylog.events.PresenceRemovedActivity;
import br.org.gam.api.shared.activitylog.events.PresenceUpdatedActivity;
import br.org.gam.api.shared.activitylog.events.GamLocationCreatedActivity;
import br.org.gam.api.shared.activitylog.events.GamLocationRemovedActivity;
import br.org.gam.api.shared.activitylog.events.GamLocationUpdatedActivity;
import br.org.gam.api.rbac.role.domain.SystemRole;
import java.util.Map;
import java.util.UUID;
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
                "previousStatus", activity.previousStatus(),
                "newStatus", activity.newStatus()
        ));
        if (activity.accountId() != null) metadata.put("accountId", activity.accountId());
        if (activity.accountId() == null) {
            metadata.put("rolesAdded", java.util.List.of());
            metadata.put("rolesRemoved", java.util.List.of());
        }
        if (activity.roleAdded() != null) metadata.put("roleAdded", activity.roleAdded());
        if (activity.roleRemoved() != null) metadata.put("roleRemoved", activity.roleRemoved());
        if (activity.roleAddedId() != null) metadata.put("roleAddedId", activity.roleAddedId());
        if (activity.roleRemovedId() != null) metadata.put("roleRemovedId", activity.roleRemovedId());
        if (activity.additionallyRemovedRoleId() != null) {
            metadata.put("additionallyRemovedRoleId", activity.additionallyRemovedRoleId());
        }
        if (activity.secondAdditionallyRemovedRoleId() != null) {
            metadata.put("secondAdditionallyRemovedRoleId", activity.secondAdditionallyRemovedRoleId());
        }
        activityLogger.log(
                activity.action(),
                ActivityTargetType.MEMBER,
                activity.memberId(),
                activity.reason(),
                null,
                metadata
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(CoordinatorChangedActivity activity) {
        activityLogger.log(
                activity.action(),
                ActivityTargetType.MEMBER,
                activity.memberId(),
                activity.reason(),
                null,
                Map.of(
                        "accountId", activity.accountId(),
                        "roleId", activity.coordRoleId()
                )
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(OratorioCoordinatorChangedActivity activity) {
        activityLogger.log(
                activity.action(),
                ActivityTargetType.MEMBER,
                activity.memberId(),
                activity.reason(),
                null,
                Map.of(
                        "accountId", activity.accountId(),
                        "roleId", activity.roleId()
                )
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(ModuleActivity activity) {
        activityLogger.log(
                activity.action(),
                activity.targetType(),
                activity.targetId(),
                activity.reason(),
                activity.summary(),
                activity.metadata()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(MemberRegisteredActivity activity) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("accountId", activity.accountId());
        metadata.put("newStatus", "ACTIVE");
        if (activity.roleAddedId() != null) metadata.put("roleAddedId", activity.roleAddedId());
        if (activity.roleRemovedId() != null) metadata.put("roleRemovedId", activity.roleRemovedId());
        activityLogger.log(
                ActivityAction.MEMBER_REGISTERED,
                ActivityTargetType.MEMBER,
                activity.memberId(),
                activity.reason(),
                null,
                metadata
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(MembershipSolicitationActivity activity) {
        Map<String, Object> metadata = new HashMap<>();
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
                null,
                metadata
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(AccountRoleAddedActivity activity) {
        logAccountRoleActivity(
                ActivityAction.ACCOUNT_ROLE_ADDED,
                activity.accountRoleId(),
                activity.accountId(),
                activity.roleId(),
                activity.roleName(),
                activity.reason(),
                activity.actorKind()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(AccountRoleRemovedActivity activity) {
        logAccountRoleActivity(
                ActivityAction.ACCOUNT_ROLE_REMOVED,
                activity.accountRoleId(),
                activity.accountId(),
                activity.roleId(),
                activity.roleName(),
                activity.reason(),
                activity.actorKind()
        );
    }

    private void logAccountRoleActivity(
            ActivityAction action,
            UUID accountRoleId,
            UUID accountId,
            UUID roleId,
            String roleName,
            String reason,
            ActivityActorKind actorKind
    ) {
        Map<String, Object> metadata = Map.of(
                "accountId", accountId,
                "roleId", roleId,
                "systemManaged", SystemRole.fromCode(roleName).isPresent()
        );
        if (actorKind == ActivityActorKind.DEVELOPER) {
            activityLogger.logDeveloper(
                    action,
                    ActivityTargetType.ACCOUNT_ROLE_ASSIGNMENT,
                    accountRoleId,
                    reason,
                    metadata
            );
            return;
        }
        activityLogger.log(
                action,
                ActivityTargetType.ACCOUNT_ROLE_ASSIGNMENT,
                accountRoleId,
                reason,
                null,
                metadata
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(EventCreatedActivity activity) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", activity.eventType().name());
        metadata.put("status", activity.status().name());
        metadata.put("gamLocationId", activity.gamLocationId());
        metadata.put("requiredPermissionId", activity.requiredPermissionId());
        activityLogger.log(
                ActivityAction.EVENT_CREATED,
                ActivityTargetType.EVENT,
                activity.eventId(),
                null,
                null,
                metadata
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(EventChangedActivity activity) {
        activityLogger.log(
                activity.action(), ActivityTargetType.EVENT, activity.eventId(), activity.reason(),
                activity.summary(), activity.metadata()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(OratorioCreatedActivity activity) {
        activityLogger.log(
                ActivityAction.ORATORIO_CREATED,
                ActivityTargetType.ORATORIO,
                activity.oratorioId(),
                null,
                null,
                Map.of()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(PresenceRegisteredActivity activity) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("memberId", activity.memberId());
        metadata.put("eventId", activity.eventId());
        metadata.put("observationsPresent", activity.observations() != null);
        activityLogger.log(
                ActivityAction.PRESENCE_REGISTERED,
                ActivityTargetType.PRESENCE,
                activity.presenceId(),
                null,
                null,
                metadata
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(PresenceUpdatedActivity activity) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("memberId", activity.memberId());
        metadata.put("eventId", activity.eventId());
        metadata.put("previousObservationsPresent", activity.previousObservations() != null);
        metadata.put("newObservationsPresent", activity.newObservations() != null);
        activityLogger.log(
                ActivityAction.PRESENCE_UPDATED,
                ActivityTargetType.PRESENCE,
                activity.presenceId(),
                null,
                null,
                metadata
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(PresenceRemovedActivity activity) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("memberId", activity.memberId());
        metadata.put("eventId", activity.eventId());
        metadata.put("observationsPresent", activity.observations() != null);
        activityLogger.log(
                ActivityAction.PRESENCE_REMOVED,
                ActivityTargetType.PRESENCE,
                activity.presenceId(),
                activity.reason(),
                null,
                metadata
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(GamLocationCreatedActivity activity) {
        activityLogger.log(
                ActivityAction.GAM_LOCATION_CREATED,
                ActivityTargetType.GAM_LOCATION,
                activity.locationId(),
                null,
                null,
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
                null,
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
                null,
                Map.of()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(DeveloperMaintenanceActivity activity) {
        if (activity.action() == ActivityAction.DEVELOPER_VIEWED_SOFT_DELETED_RECORDS) {
            activityLogger.logScope(
                    activity.action(),
                    targetTypeForTable(activity.table()),
                    "SOFT_DELETED_RECORDS",
                    activity.reason(),
                    activity.metadata()
            );
            return;
        }
        if (activity.action() == ActivityAction.MEMBER_INFORMATION_IMPORTED) {
            activityLogger.log(
                    activity.action(), ActivityTargetType.MEMBER_INFORMATION_IMPORT_BATCH,
                    activity.targetId(), activity.reason(), null, activity.metadata());
            return;
        }
        activityLogger.log(
                activity.action(),
                targetTypeForTable(activity.table()),
                activity.targetId(),
                activity.reason(),
                null,
                Map.of()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(AccountRegisteredActivity activity) {
        activityLogger.logAnonymous(
                ActivityAction.ACCOUNT_REGISTERED,
                ActivityTargetType.ACCOUNT,
                activity.accountId(),
                null,
                Map.of()
        );
    }

    private ActivityTargetType targetTypeForTable(String table) {
        return switch (table) {
            case "accounts" -> ActivityTargetType.ACCOUNT;
            case "roles" -> ActivityTargetType.ROLE;
            case "permissions" -> ActivityTargetType.PERMISSION;
            case "account_roles" -> ActivityTargetType.ACCOUNT_ROLE_ASSIGNMENT;
            case "role_permissions" -> ActivityTargetType.ROLE_PERMISSION_ASSIGNMENT;
            case "gam_locations" -> ActivityTargetType.GAM_LOCATION;
            case "events" -> ActivityTargetType.EVENT;
            case "members" -> ActivityTargetType.MEMBER;
            case "member_information_import_batches" -> ActivityTargetType.MEMBER_INFORMATION_IMPORT_BATCH;
            case "presences" -> ActivityTargetType.PRESENCE;
            case "oratorios" -> ActivityTargetType.ORATORIO;
            case "oratorianos" -> ActivityTargetType.ORATORIANO;
            default -> throw new IllegalArgumentException("Unsupported activity target table: " + table);
        };
    }
}
