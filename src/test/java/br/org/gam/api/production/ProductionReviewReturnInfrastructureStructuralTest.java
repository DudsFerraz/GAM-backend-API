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
@DisplayName("Structure - production deployment review-return infrastructure contracts")
class ProductionReviewReturnInfrastructureStructuralTest {

    private static final Path PRODUCTION_COMPOSE = Path.of(
            "deploy", "production", "compose.yml"
    );
    private static final Path PRODUCTION_CADDYFILE = Path.of(
            "deploy", "production", "Caddyfile"
    );
    private static final Path RELEASE_PLAYBOOK = Path.of(
            "deploy", "production", "ansible", "deploy-release.yml"
    );
    private static final Path INVALID_DATABASE_POLICY_MANIFEST = Path.of(
            "src", "test", "resources", "production", "fixtures",
            "release-manifest-invalid-database-policy.yml"
    );
    private static final Path INVALID_BACKEND_DIGEST_MANIFEST = Path.of(
            "src", "test", "resources", "production", "fixtures",
            "release-manifest-invalid-backend-digest.yml"
    );
    private static final Path INVALID_FORMAT_MANIFEST = Path.of(
            "src", "test", "resources", "production", "fixtures",
            "release-manifest-invalid-format.yml"
    );
    private static final Path INCOMPLETE_ROLLBACK_MANIFEST = Path.of(
            "src", "test", "resources", "production", "fixtures",
            "release-manifest-incomplete-rollback.yml"
    );
    private static final Path PROXY_GATE_STATE = Path.of(
            "src", "test", "resources", "production", "fixtures",
            "proxy-gate-dependencies-unhealthy-state.env"
    );
    private static final Path LOCK_FAILURE_STATE = Path.of(
            "src", "test", "resources", "production", "fixtures",
            "deployment-lock-owner-write-failure-state.env"
    );
    private static final Path FLYWAY_SELECTED_OVERRIDE_STATE = Path.of(
            "src", "test", "resources", "production", "fixtures",
            "flyway-selected-override-state.env"
    );
    private static final Path MANIFEST_SELECTED_ARTIFACT_STATE = Path.of(
            "src", "test", "resources", "production", "fixtures",
            "manifest-selected-artifact-state.env"
    );
    private static final Path FIXTURE_RELEASE_MANIFEST = Path.of(
            "src", "test", "resources", "production", "fixtures", "release-manifest.yml"
    );
    private static final Path RESPONSE_SENSITIVE_HEADERS_STATE = Path.of(
            "src", "test", "resources", "production", "fixtures",
            "response-sensitive-headers-state.env"
    );
    private static final Path BACKUP_BRANCH_STATE = Path.of(
            "src", "test", "resources", "production", "fixtures", "backup-branch-state.env"
    );
    private static final Path ROUTING_BOUNDARY_STATE = Path.of(
            "src", "test", "resources", "production", "fixtures", "routing-boundary-state.env"
    );
    private static final Path MAINTENANCE_ROLLBACK_STATE = Path.of(
            "src", "test", "resources", "production", "fixtures",
            "maintenance-rollback-transition-state.env"
    );

    @Test
    @DisplayName("REQ-WEB-006 - access logs redact credential-bearing response headers")
    void caddyAccessLogShouldRedactSetCookieFromResponseHeaders() throws IOException {
        String caddy = requiredFile(PRODUCTION_CADDYFILE).toLowerCase();

        assertThat(caddy)
                .contains("resp_headers>set-cookie delete")
                .doesNotContain("response>headers>set-cookie delete");
    }

    @Test
    @DisplayName("REQ-OPS-011/012 and ADR-0028 - proxy gate remains available when private dependencies are unhealthy")
    void caddyShouldNotWaitForBackendOrPostgresBeforeServingProxyGateResponses() throws IOException {
        String compose = requiredFile(PRODUCTION_COMPOSE).toLowerCase();
        String caddy = requiredFile(PRODUCTION_CADDYFILE).toLowerCase();
        String proxyGateState = requiredFile(PROXY_GATE_STATE).toLowerCase();
        String caddyService = section(compose, "\n  caddy:\n", "\n  postgres:\n");

        assertThat(proxyGateState)
                .contains("backend_health=unhealthy")
                .contains("postgres_health=unhealthy")
                .contains("expected_static_commissioning_status=503")
                .contains("expected_maintenance_status=503")
                .contains("expected_http_redirect_status=308")
                .contains("expected_tls_listener=true");
        assertThat(caddyService).doesNotContain("depends_on:");
        assertThat(compose).contains("\"443:443\"");
        assertThat(caddy)
                .contains("respond")
                .contains("503");
    }

    @Test
    @DisplayName("REQ-OPS-008/009 and ADR-0024 - maintenance transitions preserve the selected or previous backend override")
    void caddyTransitionHandlerShouldPreserveTheActiveReleasePair() throws IOException {
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();
        String handlers = playbook.substring(playbook.indexOf("handlers:"));
        String rollback = section(
                playbook,
                "database-compatible application rollback",
                "database-incompatible failure retains maintenance response"
        );

        boolean hasReleaseOverride = handlers.contains("compose.override")
                || handlers.contains("compose_override")
                || handlers.contains("active_release");
        assertThat(hasReleaseOverride)
                .as("recreating Caddy must use the active release compose override")
                .isTrue();
        assertThat(countOccurrences(handlers, "-f"))
                .as("the Caddy transition command must include the base file and active override")
                .isGreaterThanOrEqualTo(2);
        assertThat(rollback)
                .contains("frontend_previous_link")
                .contains("compose.override");
    }

    @Test
    @DisplayName("REQ-OPS-008/009 - typed database policy values fail closed")
    void databasePolicyValidationShouldRejectNullAndQuotedBooleanValues() throws IOException {
        String invalidManifest = requiredFile(INVALID_DATABASE_POLICY_MANIFEST).toLowerCase();
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();
        String preTasks = section(playbook, "pre_tasks:", "tasks:");

        assertThat(invalidManifest)
                .contains("database_change: null")
                .contains("database_rollback_compatible: \"false\"");
        boolean databaseChangeTypeCheck = preTasks.contains("database_change is boolean")
                || preTasks.contains("database_change | type_debug");
        boolean databaseRollbackTypeCheck = preTasks.contains(
                "database_rollback_compatible is boolean"
        ) || preTasks.contains("database_rollback_compatible | type_debug");
        assertThat(databaseChangeTypeCheck && databaseRollbackTypeCheck)
                .as("defined database policy fields must be actual YAML booleans")
                .isTrue();
    }

    @Test
    @DisplayName("REQ-OPS-008 - partial lock acquisition always removes the lock directory")
    void deploymentLockCleanupShouldCoverOwnerWriteFailure() throws IOException {
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();
        String lockAcquisition = section(
                playbook,
                "acquire exclusive deployment lock",
                "create release and operation directories"
        );
        String always = playbook.substring(playbook.indexOf("always:"));
        String failureState = requiredFile(LOCK_FAILURE_STATE).toLowerCase();

        assertThat(failureState)
                .contains("lock_directory_created=true")
                .contains("lock_owner_write=failed")
                .contains("expected_lock_directory_removed=true");

        int mkdir = lockAcquisition.indexOf("mkdir \"{{ deployment_lock_directory }}\"");
        int markAcquired = lockAcquisition.indexOf("mark deployment lock as acquired");
        int ownerWrite = lockAcquisition.indexOf("printf");
        boolean marksBeforeOwnerWrite = mkdir >= 0
                && markAcquired > mkdir
                && ownerWrite > markAcquired;
        boolean cleanupDoesNotDependOnlyOnFlag = !always.contains(
                "when: deployment_lock_acquired | bool"
        ) || always.contains("deployment_lock_result")
                || (always.contains("ansible.builtin.stat:") && always.contains("deployment_lock"));

        assertThat(marksBeforeOwnerWrite || cleanupDoesNotDependOnlyOnFlag)
                .as("lock cleanup must cover mkdir success followed by owner-file failure")
                .isTrue();
    }

    @Test
    @DisplayName("REQ-OPS-008/009 - manifest validation enforces format, digest identity, and complete rollback retention")
    void manifestValidationShouldEnforceCompleteReleaseInvariants() throws IOException {
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();
        String validation = section(
                playbook,
                "validate immutable release-manifest identity",
                "require deployment inputs"
        );

        assertThat(requiredFile(INVALID_BACKEND_DIGEST_MANIFEST)).contains("digest:");
        assertThat(requiredFile(INVALID_FORMAT_MANIFEST)).contains("format_version: 2");
        assertThat(requiredFile(INCOMPLETE_ROLLBACK_MANIFEST))
                .doesNotContain("retain_frontend_checksum:");
        assertThat(validation)
                .contains("format_version")
                .contains("retain_backend_digest")
                .contains("retain_frontend_archive")
                .contains("retain_frontend_checksum")
                .contains("retain_manifest")
                .contains("retain_fingerprinted_assets")
                .contains("minimum_days")
                .contains("minimum_verified_releases");
        boolean backendDigestEquality = validation.contains("backend.image.split('@')")
                || validation.contains("backend.image.endswith");
        assertThat(backendDigestEquality)
                .as("the manifest backend image digest must equal the declared backend digest")
                .isTrue();
    }

    @Test
    @DisplayName("REQ-OPS-008 - Flyway validates the selected backend override before migration")
    void flywayGateShouldUseTheSelectedBackendOverride() throws IOException {
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();
        String flywayGate = section(
                playbook,
                "flyway gate",
                "compute the controller-downloaded frontend archive digest"
        );
        String state = requiredFile(FLYWAY_SELECTED_OVERRIDE_STATE).toLowerCase();
        int overrideCreation = playbook.indexOf("create the rollback compose override for this pair");
        int flywayPosition = playbook.indexOf("flyway gate");
        boolean overrideCreatedBeforeGate = overrideCreation >= 0
                && flywayPosition >= 0
                && overrideCreation < flywayPosition;
        boolean flywayUsesOverride = countOccurrences(flywayGate, "-f") >= 2
                && (flywayGate.contains("compose.override")
                || flywayGate.contains("active_release_compose_override"));

        assertThat(state)
                .contains("database_change=true")
                .contains("selected_backend_override=release-specific")
                .contains("expected_flyway_image=selected");
        assertThat(overrideCreatedBeforeGate && flywayUsesOverride)
                .as("Flyway must validate the selected backend image rather than the base Compose image")
                .isTrue();
    }

    @Test
    @DisplayName("REQ-WEB-011/013 and ADR-0028 - frontend artifact sources follow the selected manifest coordinates")
    void frontendArtifactSelectionShouldBeManifestDrivenWhileUsingIntentionalFixtureAssets() throws IOException {
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();
        String vars = section(playbook, "vars:", "pre_tasks:");
        String selectionFlow = section(
                playbook,
                "load the release-manifest model",
                "verify frontend archive structure before transfer"
        );
        String manifest = requiredFile(FIXTURE_RELEASE_MANIFEST).toLowerCase();
        Map<String, String> state = readState(MANIFEST_SELECTED_ARTIFACT_STATE);

        assertThat(state)
                .containsEntry("FRONTEND_REPOSITORY", "gam-example/gam-frontend")
                .containsEntry("FRONTEND_TAG", "v1.4.0")
                .containsEntry("FRONTEND_ARTIFACT", "gam-frontend-v1.4.0.tar.gz")
                .containsEntry("EXPECTED_MANIFEST_DRIVEN_SELECTION", "true")
                .containsEntry("EXPECTED_FIXTURE_ARTIFACTS_INTENTIONAL", "true");
        assertThat(manifest)
                .contains("repository: gam-example/gam-frontend")
                .contains("tag: v1.4.0")
                .contains("artifact: gam-frontend-v1.4.0.tar.gz")
                .contains("sha256:");

        boolean sourcesUseSelectedArtifact =
                (vars.contains("frontend_archive_source:")
                        && vars.contains("selected_release.frontend.artifact")
                        && vars.contains("frontend_checksum_source:")
                        && vars.contains("selected_release.frontend.artifact"))
                        || (selectionFlow.contains("frontend_archive_source:")
                        && selectionFlow.contains("selected_release.frontend.artifact")
                        && selectionFlow.contains("frontend_checksum_source:")
                        && selectionFlow.contains("selected_release.frontend.artifact"));
        assertThat(sourcesUseSelectedArtifact)
                .as("fixture assets must be selected from manifest coordinates, not fixed filenames")
                .isTrue();
        assertThat(vars)
                .doesNotContain("gam-frontend-v1.4.0.tar.gz")
                .doesNotContain("gam-frontend-v1.4.0.tar.gz.sha256");
        assertThat(selectionFlow)
                .contains("selected_release.frontend.repository")
                .contains("selected_release.frontend.tag")
                .contains("selected_release.frontend.artifact")
                .contains("selected_release.frontend.sha256");
    }

    @Test
    @DisplayName("REQ-WEB-006 - response access-log fields redact all credential-bearing headers")
    void caddyShouldRedactAllSensitiveResponseHeaders() throws IOException {
        String caddy = requiredFile(PRODUCTION_CADDYFILE).toLowerCase();
        Map<String, String> state = readState(RESPONSE_SENSITIVE_HEADERS_STATE);

        assertThat(state)
                .containsEntry("EXPECTED_CADDY_FIELD", "resp_headers")
                .containsEntry(
                        "EXPECTED_RESPONSE_REDACTIONS",
                        "authorization,set-cookie,x-access-token,x-refresh-token,x-csrf-token"
                );
        assertThat(caddy)
                .contains("resp_headers>authorization delete")
                .contains("resp_headers>set-cookie delete")
                .contains("resp_headers>x-access-token delete")
                .contains("resp_headers>x-refresh-token delete")
                .contains("resp_headers>x-csrf-token delete");
    }

    @Test
    @DisplayName("REQ-OPS-008 - missing and stale backups block database-changing migration before Flyway")
    void backupNegativeBranchesShouldBlockMigrationAndPreserveTheFlywayGate() throws IOException {
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();
        String backupGate = section(
                playbook,
                "enforce backup freshness before a database change",
                "prepare the versioned release directory before database validation"
        );
        String flywayGate = section(
                playbook,
                "flyway gate",
                "compute the controller-downloaded frontend archive digest"
        );
        Map<String, String> state = readState(BACKUP_BRANCH_STATE);

        assertThat(state)
                .containsEntry("MISSING_BACKUP_DATABASE_CHANGE", "true")
                .containsEntry("MISSING_BACKUP_MARKER_EXISTS", "false")
                .containsEntry("EXPECTED_MISSING_BACKUP_BLOCKS_MIGRATION", "true")
                .containsEntry("STALE_BACKUP_MARKER_EXISTS", "true")
                .containsEntry("STALE_BACKUP_AGE_SECONDS", "86401")
                .containsEntry("EXPECTED_STALE_BACKUP_BLOCKS_MIGRATION", "true")
                .containsEntry("FRESH_BACKUP_AGE_SECONDS", "86400")
                .containsEntry("EXPECTED_FRESH_BACKUP_PERMITS_FLYWAY", "true");
        assertThat(backupGate)
                .contains("backup_marker.stat.exists")
                .contains("backup_marker.stat.isreg")
                .contains("backup_marker.stat.mtime")
                .contains("backup_max_age_seconds");
        assertThat(flywayGate).contains("when: database_change_required | bool");
        assertOrdered(
                playbook,
                "inspect the latest successful backup marker",
                "enforce backup freshness before a database change",
                "flyway gate",
                "apply migration and release sequence"
        );
    }

    @Test
    @DisplayName("REQ-WEB-003/005 - API routes stay proxied while non-API routes use SPA fallback")
    void caddyRoutingBoundaryShouldKeepApiAndSpaHandlersDistinct() throws IOException {
        String caddy = requiredFile(PRODUCTION_CADDYFILE).toLowerCase();
        Map<String, String> state = readState(ROUTING_BOUNDARY_STATE);

        assertThat(state)
                .containsEntry("API_REQUEST_PATH", "/api/accounts/me")
                .containsEntry("SPA_REQUEST_PATH", "/members/123")
                .containsEntry("EXPECTED_API_HANDLER", "reverse_proxy")
                .containsEntry("EXPECTED_SPA_HANDLER", "try_files")
                .containsEntry("EXPECTED_API_BEFORE_SPA", "true");
        assertThat(caddy)
                .contains("@api path /api/*")
                .contains("handle @api")
                .contains("reverse_proxy backend:8080")
                .contains("root * /opt/gam/frontend/current/frontend")
                .contains("try_files {path} /index.html");
        assertOrdered(caddy, "handle @api", "handle {");
    }

    @Test
    @DisplayName("REQ-OPS-008/009/012 - maintenance and rollback transitions follow contract state markers")
    void maintenanceAndRollbackTransitionsShouldUseContractDrivenState() throws IOException {
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();
        String overrideSelection = section(
                playbook,
                "create the rollback compose override for this pair",
                "enable maintenance response while preserving current commissioning state"
        );
        String maintenance = section(
                playbook,
                "enable maintenance response while preserving current commissioning state",
                "inspect current release manifest before replacement"
        );
        String rollback = section(
                playbook,
                "database-compatible application rollback",
                "database-incompatible failure retains maintenance response"
        );
        String incompatible = section(
                playbook,
                "database-incompatible failure retains maintenance response",
                "stop after an unavailable previous compatible pair"
        );
        Map<String, String> state = readState(MAINTENANCE_ROLLBACK_STATE);

        assertThat(state)
                .containsEntry("EXPECTED_MAINTENANCE_BEFORE_REPLACEMENT", "true")
                .containsEntry("EXPECTED_COMPATIBLE_ROLLBACK_PREVIOUS_PAIR", "true")
                .containsEntry("EXPECTED_DATABASE_INCOMPATIBLE_MAINTENANCE", "true")
                .containsEntry("EXPECTED_ACTIVE_OVERRIDE_FOLLOWS_CURRENT", "true");
        assertThat(maintenance)
                .contains("gam_maintenance_enabled=true")
                .contains("apply maintenance response before replacement");
        assertThat(overrideSelection)
                .contains("selected_release.backend.image")
                .contains("active_release_compose_override");
        assertThat(rollback)
                .contains("frontend_previous_link")
                .contains("active_release_compose_override")
                .contains("maintenance_response_enabled");
        assertThat(incompatible)
                .contains("maintenance remains enabled")
                .contains("no automatic database downgrade");
    }

    private static String requiredFile(Path path) throws IOException {
        assertThat(Files.exists(path)).as("required production test artifact: %s", path).isTrue();
        return Files.readString(path);
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

    private static String section(String source, String startMarker, String endMarker) {
        String normalized = source.replace("\r\n", "\n");
        int start = normalized.indexOf(startMarker);
        int end = normalized.indexOf(endMarker, start + startMarker.length());
        assertThat(start).as("section start: %s", startMarker).isGreaterThanOrEqualTo(0);
        assertThat(end).as("section end: %s", endMarker).isGreaterThan(start);
        return normalized.substring(start, end);
    }

    private static int countOccurrences(String source, String marker) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(marker, offset)) >= 0) {
            count++;
            offset += marker.length();
        }
        return count;
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
