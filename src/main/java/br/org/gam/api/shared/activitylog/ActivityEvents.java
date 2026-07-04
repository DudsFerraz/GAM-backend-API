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
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class ActivityEvents {
    private final ApplicationEventPublisher applicationEventPublisher;

    public ActivityEvents(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void memberActivated(UUID memberId, UUID accountId, String previousStatus, String newStatus,
                                String roleAdded, String roleRemoved) {
        memberStatusChanged(
                ActivityAction.MEMBER_ACTIVATED, memberId, accountId, previousStatus, newStatus, roleAdded, roleRemoved,
                null);
    }

    public void memberDeactivated(UUID memberId, UUID accountId, String previousStatus, String newStatus,
                                  String roleAdded, String roleRemoved, String reason) {
        memberStatusChanged(
                ActivityAction.MEMBER_DEACTIVATED, memberId, accountId, previousStatus, newStatus, roleAdded, roleRemoved,
                reason);
    }

    private void memberStatusChanged(ActivityAction action, UUID memberId, UUID accountId, String previousStatus,
                                     String newStatus, String roleAdded, String roleRemoved, String reason) {
        applicationEventPublisher.publishEvent(new MemberStatusChangedActivity(
                action, memberId, accountId, previousStatus, newStatus, roleAdded, roleRemoved, reason));
    }

    public void accountRoleAdded(UUID accountRoleId, UUID accountId, UUID roleId, String roleName, String reason) {
        applicationEventPublisher.publishEvent(
                new AccountRoleAddedActivity(accountRoleId, accountId, roleId, roleName, reason));
    }

    public void accountRoleRemoved(UUID accountRoleId, UUID accountId, UUID roleId, String roleName, String reason) {
        applicationEventPublisher.publishEvent(
                new AccountRoleRemovedActivity(accountRoleId, accountId, roleId, roleName, reason));
    }

    public void eventCreated(UUID eventId, String title, EventType eventType, EventStatus status, UUID locationId,
                             UUID requiredPermissionId) {
        applicationEventPublisher.publishEvent(new EventCreatedActivity(
                eventId, title, eventType, status, locationId, requiredPermissionId));
    }

    public void missaCreated(UUID missaId, UUID eventId) {
        applicationEventPublisher.publishEvent(new MissaCreatedActivity(missaId, eventId));
    }

    public void oratorioCreated(UUID oratorioId, UUID eventId) {
        applicationEventPublisher.publishEvent(new OratorioCreatedActivity(oratorioId, eventId));
    }

    public void presenceRegistered(UUID presenceId, UUID memberId, UUID eventId) {
        applicationEventPublisher.publishEvent(new PresenceRegisteredActivity(presenceId, memberId, eventId));
    }

    public void developerMaintenance(ActivityAction action, UUID targetId, String table, String reason, String summary,
                                     Map<String, Object> metadata) {
        applicationEventPublisher.publishEvent(
                new DeveloperMaintenanceActivity(action, targetId, table, reason, summary, metadata));
    }
}
