package br.org.gam.api.shared.activitylog;

import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@FunctionalTest
@DisplayName("Functional - Activity registries")
class ActivityRegistryContractTest {

    private static final Set<String> REGISTERED_ACTIONS = Set.of(
            "ACCOUNT_REGISTERED",
            "ACCOUNT_ROLE_ADDED",
            "ACCOUNT_ROLE_REMOVED",
            "EVENT_CREATED",
            "EVENT_UPDATED",
            "EVENT_CANCELLED",
            "EVENT_LOCKED",
            "EVENT_FINALIZED",
            "EVENT_REOPENED",
            "EVENT_DELETED",
            "MISSA_CREATED",
            "MISSA_UPDATED",
            "MISSA_MEMBER_ASSIGNED",
            "MISSA_MEMBER_REMOVED",
            "MISSA_CANCELLED",
            "MISSA_LOCKED",
            "MISSA_FINALIZED",
            "MISSA_REOPENED",
            "MISSA_DELETED",
            "GAM_LOCATION_CREATED",
            "GAM_LOCATION_UPDATED",
            "GAM_LOCATION_REMOVED",
            "MEMBER_REGISTERED",
            "MEMBER_ACTIVATED",
            "MEMBER_DEACTIVATED",
            "MEMBER_ACCOUNT_LINKED",
            "MEMBER_PROFILE_UPDATED",
            "MEMBER_GAM_ENTRY_DATE_UPDATED",
            "MEMBER_DIETARY_RESTRICTION_UPDATED",
            "MEMBER_EXPERIENCES_UPDATED",
            "MEMBER_SACRAMENTS_UPDATED",
            "MEMBER_CONTRIBUTION_PROFILE_UPDATED",
            "MEMBER_ANNUAL_INFORMATION_READ",
            "MEMBER_INFORMATION_IMPORTED",
            "COORDINATOR_GRANTED",
            "COORDINATOR_REVOKED",
            "ORATORIO_COORDINATOR_GRANTED",
            "ORATORIO_COORDINATOR_REVOKED",
            "MEMBERSHIP_SOLICITATION_SUBMITTED",
            "MEMBERSHIP_SOLICITATION_APPROVED",
            "MEMBERSHIP_SOLICITATION_REJECTED",
            "ORATORIO_CREATED",
            "ORATORIO_PLANNING_UPDATED",
            "ORATORIO_TEAM_MEMBER_ASSIGNED",
            "ORATORIO_TEAM_MEMBER_REMOVED",
            "ORATORIO_CANCELLED",
            "ORATORIO_LOCKED",
            "ORATORIO_FINALIZED",
            "ORATORIO_REOPENED",
            "ORATORIO_DELETED",
            "ORATORIO_MEMBER_ATTENDANCE_REGISTERED",
            "ORATORIO_MEMBER_ATTENDANCE_REMOVED",
            "ORATORIANO_ATTENDANCE_REGISTERED",
            "ORATORIANO_ATTENDANCE_REMOVED",
            "ORATORIANO_REGISTERED_AND_MARKED_PRESENT",
            "ORATORIANO_REGISTERED",
            "ORATORIANO_UPDATED",
            "ORATORIANO_DELETED",
            "ORATORIANO_RESTORED",
            "ORATORIANO_FORM_DRAFT_CREATED",
            "ORATORIANO_FORM_DRAFT_UPDATED",
            "ORATORIANO_FORM_DRAFT_DELETED",
            "ORATORIANO_FORM_COMPLETED",
            "ORATORIANO_FORM_REVOKED",
            "ORATORIANO_FORM_PRINT_SNAPSHOT_CREATED",
            "ORATORIANO_FORM_PDF_RENDERED",
            "ORATORIANO_FORM_DETAIL_READ",
            "ORATORIANO_FORM_ATTACHMENTS_REPLACED",
            "ORATORIANO_FORM_ATTACHMENT_DOWNLOADED",
            "PRESENCE_REGISTERED",
            "PRESENCE_UPDATED",
            "PRESENCE_REMOVED",
            "DEVELOPER_RESTORE_EXECUTED",
            "DEVELOPER_HARD_DELETE_EXECUTED",
            "DEVELOPER_VIEWED_SOFT_DELETED_RECORDS"
    );

    private static final Set<String> REGISTERED_TARGET_TYPES = Set.of(
            "ACCOUNT",
            "ACCOUNT_ROLE_ASSIGNMENT",
            "EVENT",
            "GAM_LOCATION",
            "MEMBER",
            "MEMBER_ANNUAL_INFORMATION_RESPONSE",
            "MEMBER_INFORMATION_IMPORT_BATCH",
            "MEMBERSHIP_SOLICITATION",
            "MISSA",
            "ORATORIO",
            "ORATORIANO",
            "ORATORIANO_ATTENDANCE",
            "ORATORIANO_FORM",
            "ORATORIANO_FORM_PRINT_SNAPSHOT",
            "PRESENCE",
            "ROLE",
            "PERMISSION",
            "ROLE_PERMISSION_ASSIGNMENT"
    );

    @Test
    @DisplayName("REQ-ACTIVITY-004 and REQ-ACTIVITY-013 - action registry -> exact stable identifiers")
    void actionRegistryShouldContainExactlyTheAcceptedStableIdentifiers() {
        assertThat(namesOf(ActivityAction.values()))
                .containsExactlyInAnyOrderElementsOf(REGISTERED_ACTIONS);
    }

    @Test
    @DisplayName("REQ-ACTIVITY-005 and REQ-ACTIVITY-013 - target registry -> exact stable identifiers")
    void targetTypeRegistryShouldContainExactlyTheAcceptedStableIdentifiers() {
        assertThat(namesOf(ActivityTargetType.values()))
                .containsExactlyInAnyOrderElementsOf(REGISTERED_TARGET_TYPES);
    }

    private Set<String> namesOf(Enum<?>[] values) {
        return Arrays.stream(values)
                .map(Enum::name)
                .collect(Collectors.toSet());
    }
}
