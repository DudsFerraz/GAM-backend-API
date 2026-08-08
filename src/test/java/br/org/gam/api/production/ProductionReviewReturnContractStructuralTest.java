package br.org.gam.api.production;

import br.org.gam.api.testing.annotation.StructuralTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@StructuralTest
@DisplayName("Structure - production deployment review-return contracts")
class ProductionReviewReturnContractStructuralTest {

    private static final Path PRODUCTION_COMPOSE = Path.of(
            "deploy", "production", "compose.yml"
    );
    private static final Path PRODUCTION_CADDYFILE = Path.of(
            "deploy", "production", "Caddyfile"
    );
    private static final Path RELEASE_PLAYBOOK = Path.of(
            "deploy", "production", "ansible", "deploy-release.yml"
    );
    private static final Path MISSING_DATABASE_POLICY_MANIFEST = Path.of(
            "src", "test", "resources", "production", "fixtures",
            "release-manifest-missing-database-policy.yml"
    );
    private static final Path UNSAFE_PAIR_MANIFEST = Path.of(
            "src", "test", "resources", "production", "fixtures",
            "release-manifest-unsafe-pair.yml"
    );
    private static final Path INVALID_CHECKSUM_SIDECAR = Path.of(
            "src", "test", "resources", "production", "fixtures",
            "gam-frontend-v1.4.0-invalid-extra-newline.sha256"
    );

    @Test
    @DisplayName("REQ-WEB-005/011 - frontend current and previous links remain visible after container bind mounting")
    void frontendReleaseLinksShouldResolveInsideTheCaddyContainer() throws IOException {
        String compose = requiredFile(PRODUCTION_COMPOSE);
        String caddy = requiredFile(PRODUCTION_CADDYFILE);
        String playbook = requiredFile(RELEASE_PLAYBOOK);

        boolean absoluteHostPathStrategy = compose.contains(
                "/opt/gam/frontend:/opt/gam/frontend:ro"
        ) && caddy.contains("root * /opt/gam/frontend/current/frontend");
        boolean relativeLinkStrategy = playbook.contains(
                "src: \"releases/{{ release_id }}\""
        ) && !playbook.contains("readlink -f");

        assertThat(absoluteHostPathStrategy || relativeLinkStrategy)
                .as("the Caddy bind mount and Ansible current/previous links must share a container-visible path")
                .isTrue();
    }

    @Test
    @DisplayName("REQ-OPS-008/009 - preflight failure preserves the previous pair without entering rollback")
    void rollbackShouldRequireThatTheCurrentReleaseActuallyChanged() throws IOException {
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();
        String state = section(playbook, "derive release transaction state", "validate immutable");
        String replacement = section(
                playbook,
                "switch the entry document to the selected fingerprinted assets",
                "apply migration and release sequence"
        );
        String rollback = section(
                playbook,
                "database-compatible application rollback",
                "database-incompatible failure retains maintenance response"
        );

        assertThat(state).contains("release_changed: false");
        assertThat(replacement).contains("release_changed: true");
        assertThat(rollback).contains("release_changed | bool");
    }

    @Test
    @DisplayName("REQ-OPS-012 and ADR-0028 - approved first launch disables and records commissioning removal")
    void approvedLaunchShouldDisableAndRecordCommissioningTransition() throws IOException {
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();
        String successfulLaunch = section(
                playbook,
                "verify production release",
                "record release result"
        );

        assertThat(successfulLaunch)
                .contains("disable commissioning gate after approved launch")
                .contains("gam_commissioning_enabled=false")
                .contains("record commissioning launch transition")
                .contains("approval_record");
        assertOrdered(
                playbook,
                "verify production release",
                "disable commissioning gate after approved launch",
                "record commissioning launch transition",
                "record release result"
        );
    }

    @Test
    @DisplayName("REQ-OPS-008 - missing database policy fields fail closed")
    void manifestWithoutDatabasePolicyShouldBeRejectedBeforeMutation() throws IOException {
        String manifest = requiredFile(MISSING_DATABASE_POLICY_MANIFEST).toLowerCase();
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();
        String preTasks = section(playbook, "pre_tasks:", "tasks:");

        assertThat(manifest)
                .doesNotContain("database_change:")
                .doesNotContain("database_rollback_compatible:");
        assertThat(preTasks)
                .contains("selected_release.compatibility.database_change is defined")
                .contains("selected_release.compatibility.database_rollback_compatible is defined");
    }

    @Test
    @DisplayName("REQ-OPS-008/009 - compatible pair identifiers reject path and shell injection")
    void unsafeCompatibilityPairShouldBeRejectedBeforePathOrShellInterpolation() throws IOException {
        String manifest = requiredFile(UNSAFE_PAIR_MANIFEST);
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();
        String preTasks = section(playbook, "pre_tasks:", "tasks:");

        assertThat(manifest).contains(";touch /tmp/pwned");
        assertThat(preTasks.contains("release_id is match")
                || preTasks.contains("selected_release.compatibility.pair is match"))
                .as("the selected compatibility pair must be allowlisted before it reaches paths or shell commands")
                .isTrue();
    }

    @Test
    @DisplayName("REQ-OPS-008 - maintenance response is applied before migration or replacement")
    void maintenanceResponseShouldBeFlushedBeforeMigration() throws IOException {
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();
        String preMigration = section(
                playbook,
                "enable maintenance response",
                "apply migration and release sequence"
        );

        assertThat(preMigration)
                .contains("ansible.builtin.meta: flush_handlers");
    }

    @Test
    @DisplayName("REQ-WEB-013 and ADR-0028 - checksum sidecar requires exact sha256sum bytes and terminal newline")
    void checksumSidecarValidationShouldRejectExtraWhitespace() throws IOException {
        String invalidSidecar = requiredFile(INVALID_CHECKSUM_SIDECAR);
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();
        String checksumVerification = section(
                playbook,
                "verify frontend release metadata and archive checksum",
                "verify frontend archive structure before transfer"
        );

        assertThat(invalidSidecar)
                .endsWith("\n\n")
                .doesNotMatch("(?s)^[0-9a-f]{64}  gam-frontend-v1\\.4\\.0\\.tar\\.gz\\n$");
        assertThat(checksumVerification)
                .doesNotContain("| trim")
                .contains("rstrip=false")
                .contains("~ '\\n'");
    }

    @Test
    @DisplayName("REQ-OPS-011 - public health verification requires JSON content type")
    void publicHealthVerificationShouldAssertApplicationJsonContentType() throws IOException {
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();
        String healthVerification = section(
                playbook,
                "verify production release",
                "mark maintenance response as disabled after verification"
        );

        assertThat(healthVerification)
                .contains("public_health.headers['content-type']")
                .contains("application/json");
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

    private static void assertOrdered(String source, String... markers) {
        int previous = -1;
        for (String marker : markers) {
            int current = source.indexOf(marker);
            assertThat(current).as("marker '%s'", marker).isGreaterThan(previous);
            previous = current;
        }
    }
}
