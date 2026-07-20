package br.org.gam.api.shared.activitylog;

import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.shared.activitylog.events.AccountRoleAddedActivity;
import br.org.gam.api.shared.activitylog.events.AccountRoleRemovedActivity;
import br.org.gam.api.shared.activitylog.events.DeveloperMaintenanceActivity;
import br.org.gam.api.shared.activitylog.events.EventCreatedActivity;
import br.org.gam.api.shared.activitylog.events.MemberStatusChangedActivity;
import br.org.gam.api.shared.activitylog.events.CoordinatorChangedActivity;
import br.org.gam.api.shared.activitylog.events.MemberRegisteredActivity;
import br.org.gam.api.shared.activitylog.events.MembershipSolicitationActivity;
import br.org.gam.api.rbac.role.application.RoleEntityLoader;
import br.org.gam.api.shared.activitylog.events.MissaCreatedActivity;
import br.org.gam.api.shared.activitylog.events.OratorioCreatedActivity;
import br.org.gam.api.shared.activitylog.events.PresenceRegisteredActivity;
import br.org.gam.api.shared.activitylog.events.GamLocationCreatedActivity;
import br.org.gam.api.shared.activitylog.events.GamLocationRemovedActivity;
import br.org.gam.api.shared.activitylog.events.GamLocationUpdatedActivity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class ActivityEvents {
    private final ApplicationEventPublisher applicationEventPublisher;
    private final RoleEntityLoader roleEntityLoader;

    public ActivityEvents(ApplicationEventPublisher applicationEventPublisher, RoleEntityLoader roleEntityLoader) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.roleEntityLoader = roleEntityLoader;
    }

    public void memberActivated(UUID memberId, UUID accountId, String previousStatus, String newStatus,
                                String roleAdded, String roleRemoved, String reason) {
        memberStatusChanged(
                ActivityAction.MEMBER_ACTIVATED, memberId, accountId, previousStatus, newStatus, roleAdded, roleRemoved,
                null, reason);
    }

    public void memberDeactivated(UUID memberId, UUID accountId, String previousStatus, String newStatus,
                                  String roleAdded, String roleRemoved, String reason) {
        memberStatusChanged(
                ActivityAction.MEMBER_DEACTIVATED, memberId, accountId, previousStatus, newStatus, roleAdded, roleRemoved,
                null, reason);
    }

    public void memberDeactivated(UUID memberId, UUID accountId, String previousStatus, String newStatus,
                                  String roleAdded, String roleRemoved, UUID additionallyRemovedRoleId,
                                  String reason) {
        memberStatusChanged(
                ActivityAction.MEMBER_DEACTIVATED, memberId, accountId, previousStatus, newStatus, roleAdded, roleRemoved,
                additionallyRemovedRoleId, reason);
    }

    private void memberStatusChanged(ActivityAction action, UUID memberId, UUID accountId, String previousStatus,
                                     String newStatus, String roleAdded, String roleRemoved,
                                     UUID additionallyRemovedRoleId, String reason) {
        UUID roleAddedId = roleEntityLoader.requiredByName(roleAdded).getId();
        UUID roleRemovedId = roleEntityLoader.requiredByName(roleRemoved).getId();
        applicationEventPublisher.publishEvent(new MemberStatusChangedActivity(
                action, memberId, accountId, previousStatus, newStatus, roleAdded, roleRemoved,
                roleAddedId, roleRemovedId, additionallyRemovedRoleId, reason));
    }

    public void memberRegistered(UUID memberId, UUID accountId, UUID roleAddedId, UUID roleRemovedId, String reason) {
        applicationEventPublisher.publishEvent(
                new MemberRegisteredActivity(memberId, accountId, roleAddedId, roleRemovedId, reason)
        );
    }

    public void coordinatorGranted(UUID memberId, UUID accountId, UUID coordRoleId, String reason) {
        applicationEventPublisher.publishEvent(new CoordinatorChangedActivity(
                ActivityAction.COORDINATOR_GRANTED, memberId, accountId, coordRoleId, reason));
    }

    public void coordinatorRevoked(UUID memberId, UUID accountId, UUID coordRoleId, String reason) {
        applicationEventPublisher.publishEvent(new CoordinatorChangedActivity(
                ActivityAction.COORDINATOR_REVOKED, memberId, accountId, coordRoleId, reason));
    }

    public void membershipSolicitationSubmitted(UUID solicitationId, UUID applicantAccountId) {
        membershipSolicitation(
                ActivityAction.MEMBERSHIP_SOLICITATION_SUBMITTED, solicitationId, applicantAccountId,
                null, "PENDING", null, null, null, null
        );
    }

    public void membershipSolicitationApproved(UUID solicitationId, UUID applicantAccountId, UUID memberId,
                                                UUID roleAddedId, UUID roleRemovedId, String reason) {
        membershipSolicitation(
                ActivityAction.MEMBERSHIP_SOLICITATION_APPROVED, solicitationId, applicantAccountId,
                "PENDING", "APPROVED", memberId, roleAddedId, roleRemovedId, reason
        );
    }

    public void membershipSolicitationRejected(UUID solicitationId, UUID applicantAccountId, String reason) {
        membershipSolicitation(
                ActivityAction.MEMBERSHIP_SOLICITATION_REJECTED, solicitationId, applicantAccountId,
                "PENDING", "REJECTED", null, null, null, reason
        );
    }

    private void membershipSolicitation(ActivityAction action, UUID solicitationId, UUID applicantAccountId,
                                        String previousStatus, String newStatus, UUID memberId,
                                        UUID roleAddedId, UUID roleRemovedId, String reason) {
        applicationEventPublisher.publishEvent(new MembershipSolicitationActivity(
                action, solicitationId, applicantAccountId, previousStatus, newStatus,
                memberId, roleAddedId, roleRemovedId, reason
        ));
    }

    public void accountRoleAdded(UUID accountRoleId, UUID accountId, UUID roleId, String roleName, String reason) {
        applicationEventPublisher.publishEvent(
                new AccountRoleAddedActivity(accountRoleId, accountId, roleId, roleName, reason));
    }

    public void accountRoleRemoved(UUID accountRoleId, UUID accountId, UUID roleId, String roleName, String reason) {
        applicationEventPublisher.publishEvent(
                new AccountRoleRemovedActivity(accountRoleId, accountId, roleId, roleName, reason));
    }

    public void eventCreated(UUID eventId, String title, EventType eventType, EventStatus status, UUID gamLocationId,
                             UUID requiredPermissionId) {
        applicationEventPublisher.publishEvent(new EventCreatedActivity(
                eventId, title, eventType, status, gamLocationId, requiredPermissionId));
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

    public void gamLocationCreated(UUID locationId) {
        applicationEventPublisher.publishEvent(new GamLocationCreatedActivity(locationId));
    }

    public void gamLocationUpdated(UUID locationId, List<String> changedFields) {
        applicationEventPublisher.publishEvent(new GamLocationUpdatedActivity(locationId, List.copyOf(changedFields)));
    }

    public void gamLocationRemoved(UUID locationId, String reason, String name) {
        applicationEventPublisher.publishEvent(new GamLocationRemovedActivity(locationId, reason, name));
    }

    public void developerMaintenance(ActivityAction action, UUID targetId, String table, String reason, String summary,
                                     Map<String, Object> metadata) {
        applicationEventPublisher.publishEvent(
                new DeveloperMaintenanceActivity(action, targetId, table, reason, summary, metadata));
    }
}
