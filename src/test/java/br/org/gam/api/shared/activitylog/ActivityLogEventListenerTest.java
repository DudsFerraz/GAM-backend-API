package br.org.gam.api.shared.activitylog;

import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.shared.activitylog.events.AccountRoleAddedActivity;
import br.org.gam.api.shared.activitylog.events.AccountRoleRemovedActivity;
import br.org.gam.api.shared.activitylog.events.CoordinatorChangedActivity;
import br.org.gam.api.shared.activitylog.events.DeveloperMaintenanceActivity;
import br.org.gam.api.shared.activitylog.events.EventCreatedActivity;
import br.org.gam.api.shared.activitylog.events.MemberStatusChangedActivity;
import br.org.gam.api.shared.activitylog.events.OratorioCoordinatorChangedActivity;
import br.org.gam.api.shared.activitylog.events.OratorioCreatedActivity;
import br.org.gam.api.shared.activitylog.events.PresenceRegisteredActivity;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Activity Log Event Listener")
class ActivityLogEventListenerTest {

    @Mock
    private ActivityLogger activityLogger;

    @InjectMocks
    private ActivityLogEventListener listener;

    @Test
    @DisplayName("member status activity -> member activity log")
    void memberStatusActivityShouldMapToMemberActivityLog() {
        UUID memberId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        listener.handle(new MemberStatusChangedActivity(
                ActivityAction.MEMBER_ACTIVATED,
                memberId,
                accountId,
                "INACTIVE",
                "ACTIVE",
                "MEMBER",
                "VISITOR",
                "Returning to weekly activities"
        ));

        verify(activityLogger).log(
                ActivityAction.MEMBER_ACTIVATED,
                ActivityTargetType.MEMBER,
                memberId,
                "Returning to weekly activities",
                null,
                Map.of(
                        "accountId", accountId,
                        "previousStatus", "INACTIVE",
                        "newStatus", "ACTIVE",
                        "roleAdded", "MEMBER",
                        "roleRemoved", "VISITOR"
                )
        );
    }

    @Test
    @DisplayName("account role activities -> account role activity logs")
    void accountRoleActivitiesShouldMapToAccountRoleActivityLogs() {
        UUID accountRoleId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        listener.handle(new AccountRoleAddedActivity(accountRoleId, accountId, roleId, "ADMIN", "Grant admin access"));
        listener.handle(new AccountRoleRemovedActivity(accountRoleId, accountId, roleId, "ADMIN", "Remove admin access"));

        verify(activityLogger).log(
                eq(ActivityAction.ACCOUNT_ROLE_ADDED),
                argThat(target -> target.name().equals("ACCOUNT_ROLE_ASSIGNMENT")),
                eq(accountRoleId),
                eq("Grant admin access"),
                isNull(),
                eq(Map.of("accountId", accountId, "roleId", roleId, "systemManaged", false))
        );
        verify(activityLogger).log(
                eq(ActivityAction.ACCOUNT_ROLE_REMOVED),
                argThat(target -> target.name().equals("ACCOUNT_ROLE_ASSIGNMENT")),
                eq(accountRoleId),
                eq("Remove admin access"),
                isNull(),
                eq(Map.of("accountId", accountId, "roleId", roleId, "systemManaged", false))
        );
    }

    @Test
    @DisplayName("REQ-MEMBER-018 - Coordinator transition -> exact accountId and roleId metadata")
    void coordinatorActivityShouldUseExactMetadataWithoutPrimaryTargetDuplicates() {
        UUID memberId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        listener.handle(new CoordinatorChangedActivity(
                ActivityAction.COORDINATOR_GRANTED,
                memberId,
                accountId,
                roleId,
                "Designated by the regional coordinator"
        ));

        verify(activityLogger).log(
                ActivityAction.COORDINATOR_GRANTED,
                ActivityTargetType.MEMBER,
                memberId,
                "Designated by the regional coordinator",
                null,
                Map.of("accountId", accountId, "roleId", roleId)
        );
    }

    @Test
    @DisplayName("REQ-ORATORIO-COORD-003 - Oratorio Coordinator transition -> exact accountId and roleId metadata")
    void oratorioCoordinatorActivityShouldUseExactMetadataWithoutPrimaryTargetDuplicates() {
        UUID memberId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        listener.handle(new OratorioCoordinatorChangedActivity(
                ActivityAction.ORATORIO_COORDINATOR_REVOKED,
                memberId,
                accountId,
                roleId,
                "ORATORIO_COORD",
                "Rotation of responsibility"
        ));

        verify(activityLogger).log(
                ActivityAction.ORATORIO_COORDINATOR_REVOKED,
                ActivityTargetType.MEMBER,
                memberId,
                "Rotation of responsibility",
                null,
                Map.of("accountId", accountId, "roleId", roleId)
        );
    }

    @Test
    @DisplayName("REQ-ACTIVITY-009 and REQ-ORATORIO-001 - shared Event/Oratorio UUID -> no primary-target metadata duplicate")
    void createActivitiesShouldMapToCreateActivityLogs() {
        UUID eventId = UUID.randomUUID();
        UUID gamLocationId = UUID.randomUUID();
        UUID requiredPermissionId = UUID.randomUUID();

        listener.handle(new EventCreatedActivity(
                eventId,
                "Community Meeting",
                EventType.GENERIC,
                EventStatus.SCHEDULED,
                gamLocationId,
                requiredPermissionId
        ));
        listener.handle(new OratorioCreatedActivity(eventId, eventId));

        verify(activityLogger).log(
                ActivityAction.EVENT_CREATED,
                ActivityTargetType.EVENT,
                eventId,
                null,
                null,
                Map.of(
                        "type", EventType.GENERIC.name(),
                        "status", EventStatus.SCHEDULED.name(),
                        "gamLocationId", gamLocationId,
                        "requiredPermissionId", requiredPermissionId
                )
        );
        verify(activityLogger).log(
                ActivityAction.ORATORIO_CREATED,
                ActivityTargetType.ORATORIO,
                eventId,
                null,
                null,
                Map.of()
        );
    }

    @Test
    @DisplayName("presence activity -> presence activity log")
    void presenceActivityShouldMapToPresenceActivityLog() {
        UUID presenceId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        listener.handle(new PresenceRegisteredActivity(presenceId, memberId, eventId));

        verify(activityLogger).log(
                ActivityAction.PRESENCE_REGISTERED,
                ActivityTargetType.PRESENCE,
                presenceId,
                null,
                null,
                Map.of(
                        "memberId", memberId,
                        "eventId", eventId,
                        "observationsPresent", false
                )
        );
    }

    @Test
    @DisplayName("developer maintenance activity -> maintenance activity log")
    void developerMaintenanceActivityShouldMapToMaintenanceActivityLog() {
        UUID targetId = UUID.randomUUID();
        Map<String, Object> metadata = Map.of("table", "events", "id", targetId);

        listener.handle(new DeveloperMaintenanceActivity(
                ActivityAction.DEVELOPER_RESTORE_EXECUTED,
                targetId,
                "events",
                "manual correction",
                "Developer restored soft-deleted record events/" + targetId,
                metadata
        ));

        verify(activityLogger).log(
                ActivityAction.DEVELOPER_RESTORE_EXECUTED,
                ActivityTargetType.EVENT,
                targetId,
                "manual correction",
                null,
                Map.of()
        );
    }

    @Test
    @DisplayName("REQ-ACTIVITY-005/014 and REQ-PERSISTENCE-012 - deleted-record inspection -> typed scope without synthetic target")
    void deletedRecordInspectionShouldMapToTypedScopeTarget() {
        listener.handle(new DeveloperMaintenanceActivity(
                ActivityAction.DEVELOPER_VIEWED_SOFT_DELETED_RECORDS,
                null,
                "events",
                "Investigate deleted Event records",
                null,
                Map.of("count", 0)
        ));

        verify(activityLogger).logScope(
                ActivityAction.DEVELOPER_VIEWED_SOFT_DELETED_RECORDS,
                ActivityTargetType.EVENT,
                "SOFT_DELETED_RECORDS",
                "Investigate deleted Event records",
                Map.of("count", 0)
        );
    }
}
