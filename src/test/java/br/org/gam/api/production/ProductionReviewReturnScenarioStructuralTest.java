package br.org.gam.api.production;

import br.org.gam.api.testing.annotation.StructuralTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@StructuralTest
@DisplayName("Structure - production deployment review-return scenarios")
class ProductionReviewReturnScenarioStructuralTest {

    private static final Path RELEASE_PLAYBOOK = Path.of(
            "deploy", "production", "ansible", "deploy-release.yml"
    );
    private static final Path POST_LAUNCH_STATE = Path.of(
            "src", "test", "resources", "production", "fixtures",
            "post-launch-deployment-state.env"
    );
    private static final Path RELEASE_RECORD_FAILURE_STATE = Path.of(
            "src", "test", "resources", "production", "fixtures",
            "release-record-failure-state.env"
    );
    private static final Path FIRST_LAUNCH_FAILURE_STATE = Path.of(
            "src", "test", "resources", "production", "fixtures",
            "first-launch-health-failure-state.env"
    );
    private static final Path RELEASE_ID_COLLISION_STATE = Path.of(
            "src", "test", "resources", "production", "fixtures",
            "release-id-collision-state.env"
    );
    private static final Path FIRST_LAUNCH_STALE_STATE = Path.of(
            "src", "test", "resources", "production", "fixtures",
            "first-launch-stale-commissioning-state.env"
    );
    private static final Path ROLLBACK_OVERRIDE_FAILURE_STATE = Path.of(
            "src", "test", "resources", "production", "fixtures",
            "rollback-override-failure-state.env"
    );

    @Test
    @DisplayName("REQ-OPS-012 - routine post-launch deployment preserves a disabled commissioning gate")
    void routinePostLaunchDeploymentShouldPreserveCommissioningState() throws IOException {
        Map<String, String> state = readState(POST_LAUNCH_STATE);
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();
        String maintenanceSetup = section(
                playbook,
                "enable maintenance response",
                "mark maintenance response as enabled for the transaction"
        );

        assertThat(state)
                .containsEntry("FIRST_LAUNCH", "false")
                .containsEntry("GAM_COMMISSIONING_ENABLED", "false");
        assertThat(maintenanceSetup)
                .contains("gam_commissioning_enabled")
                .doesNotContain("gam_commissioning_enabled={{ commissioning_enabled | bool | ternary('true', 'false') }}")
                .containsPattern("gam_commissioning_enabled=.*(current|existing|preserv|previous)");
    }

    @Test
    @DisplayName("REQ-OPS-008/009 - release-record failure keeps maintenance active for rescue")
    void releaseRecordFailureShouldPreserveMaintenanceBeforeRollback() throws IOException {
        Map<String, String> state = readState(RELEASE_RECORD_FAILURE_STATE);
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();
        String successPath = section(playbook, "verify production release before approved transitions", "rescue:");
        String rescue = section(playbook, "rescue:", "always:");

        assertThat(state)
                .containsEntry("RELEASE_CHANGED", "true")
                .containsEntry("MAINTENANCE_RESPONSE_ENABLED", "true")
                .containsEntry("RELEASE_RECORD_WRITE", "failed")
                .containsEntry("EXPECTED_MAINTENANCE_RESPONSE_ENABLED", "true");

        int record = successPath.indexOf("record release result");
        int clear = successPath.indexOf("mark maintenance response as disabled after verification");
        boolean recordBeforeClear = record >= 0 && clear >= 0 && record < clear;
        boolean explicitRecordFailureRescue = rescue.contains("release record failure")
                && rescue.contains("maintenance_response_enabled: true");

        assertThat(recordBeforeClear || explicitRecordFailureRescue)
                .as("a release-record failure after replacement must retain maintenance before rollback")
                .isTrue();
    }

    @Test
    @DisplayName("REQ-OPS-012 and ADR-0028 - commissioning transition is recorded before health and restored on failed launch")
    void firstLaunchFailureShouldRecordTransitionBeforeVerificationAndRestoreTheGate() throws IOException {
        Map<String, String> state = readState(FIRST_LAUNCH_FAILURE_STATE);
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();
        String rescue = section(
                playbook,
                "re-enable commissioning gate after first-launch verification failure",
                "record failed release result"
        );

        assertThat(state)
                .containsEntry("FIRST_LAUNCH", "true")
                .containsEntry("PUBLIC_HEALTH", "failed")
                .containsEntry("EXPECTED_TRANSITION_RECORDED_BEFORE_HEALTH", "true")
                .containsEntry("EXPECTED_COMMISSIONING_ENABLED_AFTER_RESCUE", "true");

        int transitionRecord = playbook.indexOf("record commissioning launch transition");
        int publicHealth = playbook.indexOf("verify public production health after approved transition");
        assertThat(transitionRecord)
                .as("commissioning transition record marker")
                .isGreaterThanOrEqualTo(0);
        assertThat(publicHealth)
                .as("public health verification marker")
                .isGreaterThanOrEqualTo(0);
        assertThat(transitionRecord)
                .as("approval/configuration transition must be recorded before external verification")
                .isLessThan(publicHealth);

        assertThat(rescue)
                .contains("first_launch | bool")
                .contains("gam_commissioning_enabled=true")
                .contains("apply first-launch commissioning transition");
    }

    @Test
    @DisplayName("REQ-WEB-011 and REQ-OPS-008/009 - changed artifact coordinates receive a distinct retained release identity")
    void samePairWithChangedArtifactCoordinatesShouldNotReuseReleaseDirectory() throws IOException {
        Map<String, String> state = readState(RELEASE_ID_COLLISION_STATE);
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();
        String derivation = section(
                playbook,
                "derive release transaction state",
                "validate immutable release-manifest identity"
        );

        assertThat(state)
                .containsEntry("PAIR", "frontend-v1.4.0-backend-fixture")
                .containsEntry("EXPECTED_RELEASE_IDS_DIFFER", "true")
                .containsEntry("FRONTEND_ARTIFACT_A", "gam-frontend-v1.4.0.tar.gz")
                .containsEntry("FRONTEND_ARTIFACT_B", "gam-frontend-v1.4.1.tar.gz");
        boolean backendCoordinateIncluded = derivation.contains("selected_release.backend.digest")
                || derivation.contains("selected_release.backend.image");
        boolean frontendCoordinateIncluded = derivation.contains("selected_release.frontend.sha256")
                || derivation.contains("selected_release.frontend.artifact")
                || derivation.contains("selected_release.frontend.tag");
        assertThat(backendCoordinateIncluded && frontendCoordinateIncluded)
                .as("release_id must distinguish immutable backend and frontend artifact coordinates")
                .isTrue();
    }

    @Test
    @DisplayName("REQ-OPS-008/009 - failed-release recording cannot prevent compatible rollback")
    void failedReleaseRecordFailureShouldNotSkipCompatibleRollback() throws IOException {
        Map<String, String> state = readState(RELEASE_RECORD_FAILURE_STATE);
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();
        String rescue = section(playbook, "rescue:", "always:");
        String failedRecord = section(
                rescue,
                "record failed release result",
                "stop after preflight failure before release change"
        );

        assertThat(state)
                .containsEntry("RELEASE_CHANGED", "true")
                .containsEntry("RELEASE_RECORD_WRITE", "failed")
                .containsEntry("EXPECTED_MAINTENANCE_RESPONSE_ENABLED", "true")
                .containsEntry("EXPECTED_COMPATIBLE_ROLLBACK_ATTEMPTED", "true")
                .containsEntry("EXPECTED_RECORD_FAILURE_TERMINAL", "true");
        int failedRecordPosition = rescue.indexOf("record failed release result");
        int rollbackPosition = rescue.indexOf("database-compatible application rollback");
        boolean recordAfterRollback = failedRecordPosition > rollbackPosition
                && rollbackPosition >= 0;
        boolean recordFailureCapturedForLaterTerminalFailure = failedRecord.contains("register:")
                && rescue.contains("failed_release_record")
                && rescue.contains("ansible.builtin.fail");
        boolean recordFailureIsTerminal = !failedRecord.contains("failed_when: false")
                && (recordAfterRollback || recordFailureCapturedForLaterTerminalFailure);
        assertThat(recordFailureIsTerminal)
                .as("failed-release recording must remain visible/terminal while compatible rollback is attempted")
                .isTrue();
    }

    @Test
    @DisplayName("REQ-OPS-009 - rollback failure restores a coherent current link and active Compose override")
    void rollbackFailureShouldNotLeaveTheSelectedOverrideActive() throws IOException {
        Map<String, String> state = readState(ROLLBACK_OVERRIDE_FAILURE_STATE);
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();
        String rollback = section(
                playbook,
                "database-compatible application rollback",
                "database-incompatible failure retains maintenance response"
        );
        String rollbackRescue = section(rollback, "rescue:", "when:");

        assertThat(state)
                .containsEntry("ROLLBACK_COMMAND", "failed")
                .containsEntry("EXPECTED_ACTIVE_OVERRIDE", "previous")
                .containsEntry("EXPECTED_CURRENT_LINK", "previous")
                .containsEntry("EXPECTED_COHERENT_PAIR", "true");
        int restoreCurrent = rollback.indexOf("restore previous compatible frontend release");
        int selectPrevious = rollback.indexOf("select the previous release override for transition handlers");
        boolean selectBeforeCurrentRestore = selectPrevious >= 0
                && restoreCurrent >= 0
                && selectPrevious < restoreCurrent;
        boolean failureCleanupRepairsOverride = rollbackRescue.contains(
                "active_release_compose_override"
        ) && rollbackRescue.contains("frontend_previous_link");
        assertThat(selectBeforeCurrentRestore || failureCleanupRepairsOverride)
                .as("rollback failure must keep current and active Compose override on the same pair")
                .isTrue();
    }

    @Test
    @DisplayName("REQ-OPS-012 and ADR-0028 - first launch enables commissioning before rollout when runtime state is stale")
    void firstLaunchShouldForceTheCommissioningGateClosedBeforePublicExposure() throws IOException {
        Map<String, String> state = readState(FIRST_LAUNCH_STALE_STATE);
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();
        String beforeRollout = section(
                playbook,
                "read current commissioning gate state before maintenance",
                "apply migration and release sequence"
        );
        int transitionRecord = playbook.indexOf("record commissioning launch transition");
        int publicHealth = playbook.indexOf("verify public production health after approved transition");

        assertThat(state)
                .containsEntry("FIRST_LAUNCH", "true")
                .containsEntry("CURRENT_GAM_COMMISSIONING_ENABLED", "false")
                .containsEntry("EXPECTED_GAM_COMMISSIONING_ENABLED_DURING_ROLLOUT", "true")
                .containsEntry("EXPECTED_TRANSITION_RECORDED_BEFORE_PUBLIC_HEALTH", "true");
        assertThat(beforeRollout.contains("first_launch | bool")
                && beforeRollout.contains("gam_commissioning_enabled=true"))
                .as("first launch must force commissioning enabled before rollout despite stale runtime state")
                .isTrue();
        assertThat(transitionRecord)
                .as("commissioning transition record marker")
                .isGreaterThanOrEqualTo(0);
        assertThat(publicHealth)
                .as("public health verification marker")
                .isGreaterThanOrEqualTo(0);
        assertThat(transitionRecord)
                .as("approved commissioning transition must be recorded before public verification")
                .isLessThan(publicHealth);
    }

    private static Map<String, String> readState(Path path) throws IOException {
        Map<String, String> state = new LinkedHashMap<>();
        for (String line : requiredFile(path).split("\\R")) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] pair = line.split("=", 2);
            assertThat(pair).as("state fixture line: %s", line).hasSize(2);
            state.put(pair[0].trim(), pair[1].trim());
        }
        return state;
    }

    private static String requiredFile(Path path) throws IOException {
        assertThat(Files.exists(path)).as("required production test artifact: %s", path).isTrue();
        return Files.readString(path);
    }

    private static String section(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertThat(start).as("section start: %s", startMarker).isGreaterThanOrEqualTo(0);
        assertThat(end).as("section end: %s", endMarker).isGreaterThan(start);
        return source.substring(start, end);
    }
}
