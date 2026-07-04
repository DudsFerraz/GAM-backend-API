package br.org.gam.api.shared.activitylog;

import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.shared.activitylog.events.AccountRoleAddedActivity;
import br.org.gam.api.shared.activitylog.events.AccountRoleRemovedActivity;
import br.org.gam.api.shared.activitylog.events.DeveloperMaintenanceActivity;
import br.org.gam.api.shared.activitylog.events.EventCreatedActivity;
import br.org.gam.api.shared.activitylog.events.MemberStatusChangedActivity;
import br.org.gam.api.shared.activitylog.events.MissaCreatedActivity;
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
                "PENDENT",
                "ACTIVE",
                "MEMBER",
                "VISITOR",
                null
        ));

        verify(activityLogger).log(
                ActivityAction.MEMBER_ACTIVATED,
                ActivityTargetType.MEMBER,
                memberId,
                null,
                "Member status changed from PENDENT to ACTIVE",
                Map.of(
                        "memberId", memberId,
                        "accountId", accountId,
                        "previousStatus", "PENDENT",
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
                ActivityAction.ACCOUNT_ROLE_ADDED,
                ActivityTargetType.ACCOUNT_ROLE,
                accountRoleId,
                "Grant admin access",
                "Role ADMIN added to account " + accountId,
                Map.of("accountId", accountId, "roleId", roleId, "roleName", "ADMIN")
        );
        verify(activityLogger).log(
                ActivityAction.ACCOUNT_ROLE_REMOVED,
                ActivityTargetType.ACCOUNT_ROLE,
                accountRoleId,
                "Remove admin access",
                "Role " + roleId + " removed from account " + accountId,
                Map.of("accountId", accountId, "roleId", roleId, "roleName", "ADMIN")
        );
    }

    @Test
    @DisplayName("create activities -> create activity logs")
    void createActivitiesShouldMapToCreateActivityLogs() {
        UUID eventId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID requiredPermissionId = UUID.randomUUID();

        listener.handle(new EventCreatedActivity(
                eventId,
                "Sunday Mass",
                EventType.MISSA,
                EventStatus.SCHEDULED,
                locationId,
                requiredPermissionId
        ));
        listener.handle(new MissaCreatedActivity(eventId, eventId));
        listener.handle(new OratorioCreatedActivity(eventId, eventId));

        verify(activityLogger).log(
                ActivityAction.EVENT_CREATED,
                ActivityTargetType.EVENT,
                eventId,
                null,
                "Event created: Sunday Mass",
                Map.of(
                        "eventId", eventId,
                        "eventType", EventType.MISSA.name(),
                        "status", EventStatus.SCHEDULED.name(),
                        "locationId", locationId,
                        "requiredPermissionId", requiredPermissionId
                )
        );
        verify(activityLogger).log(
                ActivityAction.MISSA_CREATED,
                ActivityTargetType.MISSA,
                eventId,
                null,
                "Missa created for event " + eventId,
                Map.of("missaId", eventId, "eventId", eventId)
        );
        verify(activityLogger).log(
                ActivityAction.ORATORIO_CREATED,
                ActivityTargetType.ORATORIO,
                eventId,
                null,
                "Oratorio created for event " + eventId,
                Map.of("oratorioId", eventId, "eventId", eventId)
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
                "Presence registered for member " + memberId + " and event " + eventId,
                Map.of("presenceId", presenceId, "memberId", memberId, "eventId", eventId)
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
                ActivityTargetType.MAINTENANCE_RECORD,
                targetId,
                "manual correction",
                "Developer restored soft-deleted record events/" + targetId,
                metadata
        );
    }
}
