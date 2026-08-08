package br.org.gam.api.production;

import br.org.gam.api.testing.annotation.StructuralTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@StructuralTest
@DisplayName("Structure - expanded production deployment contracts")
class ProductionDeploymentExpandedContractStructuralTest {

    private static final Path PRODUCTION_COMPOSE = Path.of(
            "deploy", "production", "compose.yml"
    );
    private static final Path PRODUCTION_CADDYFILE = Path.of(
            "deploy", "production", "Caddyfile"
    );
    private static final Path RELEASE_MANIFEST = Path.of(
            "deploy", "production", "release-manifest.yml"
    );
    private static final Path RELEASE_PLAYBOOK = Path.of(
            "deploy", "production", "ansible", "deploy-release.yml"
    );
    private static final Path FIXTURE_MANIFEST = Path.of(
            "src", "test", "resources", "production", "fixtures", "release-manifest.yml"
    );

    @Test
    @DisplayName("REQ-OPS-008/011/012 - Caddy serves proxy gates independently while the backend waits for PostgreSQL")
    void composeShouldKeepProxyGateIndependentFromPrivateReadiness() throws IOException {
        String compose = requiredFile(PRODUCTION_COMPOSE);
        String caddy = serviceBlock(compose, "caddy");
        String backend = serviceBlock(compose, "backend");
        String postgres = serviceBlock(compose, "postgres");

        assertThat(postgres).contains("healthcheck:");
        assertThat(backend).contains("healthcheck:");
        assertThat(backend).contains("condition: service_healthy");
        assertThat(caddy)
                .doesNotContain("depends_on:")
                .contains("\"80:80\"")
                .contains("\"443:443\"");
    }

    @Test
    @DisplayName("REQ-WEB-011/013 - production identity matches the deterministic fixture pair")
    void productionManifestAndComposeShouldUseFixturePinnedIdentities() throws IOException {
        String fixtureManifest = requiredFile(FIXTURE_MANIFEST);
        String productionManifest = requiredFile(RELEASE_MANIFEST);
        String compose = requiredFile(PRODUCTION_COMPOSE);

        String backendImage = scalar(fixtureManifest, "image");
        assertThat(productionManifest)
                .contains("image: " + backendImage)
                .contains("repository: " + scalar(fixtureManifest, "repository"))
                .contains("tag: " + scalar(fixtureManifest, "tag"))
                .contains("artifact: " + scalar(fixtureManifest, "artifact"))
                .contains("sha256: " + scalar(fixtureManifest, "sha256"));
        assertThat(compose).contains("image: " + backendImage);
    }

    @Test
    @DisplayName("REQ-OPS-012 - commissioning and maintenance intercept before API and SPA handlers")
    void caddyShouldOrderCommissioningMaintenanceApiAndSpaHandlers() throws IOException {
        String caddy = requiredFile(PRODUCTION_CADDYFILE).toLowerCase();

        assertThat(caddy)
                .contains("handle @commissioning_denied")
                .contains("not remote_ip")
                .contains("handle @maintenance_enabled")
                .contains("respond \"service unavailable\" 503")
                .contains("header cache-control \"no-store\"")
                .contains("handle @api")
                .contains("handle {");
        assertOrdered(
                caddy,
                "handle @commissioning_denied",
                "handle @maintenance_enabled",
                "handle @api",
                "handle {"
        );
    }

    @Test
    @DisplayName("REQ-WEB-006 - proxy logging removes every credential-bearing field")
    void caddyShouldRedactTokenHeadersInAdditionToCookies() throws IOException {
        String caddy = requiredFile(PRODUCTION_CADDYFILE).toLowerCase();

        assertThat(caddy)
                .contains("request>headers>authorization delete")
                .contains("request>headers>cookie delete")
                .contains("resp_headers>set-cookie delete")
                .containsPattern("request>headers>[^\\r\\n]*access-token[^\\r\\n]*(delete|redact)")
                .containsPattern("request>headers>[^\\r\\n]*refresh-token[^\\r\\n]*(delete|redact)")
                .containsPattern("request>headers>[^\\r\\n]*csrf-token[^\\r\\n]*(delete|redact)");
    }

    @Test
    @DisplayName("REQ-WEB-006 - proxy applies baseline browser protections without HSTS")
    void caddyShouldApplyBaselineBrowserHeadersWithoutPrematureHsts() throws IOException {
        String caddy = requiredFile(PRODUCTION_CADDYFILE).toLowerCase();

        assertThat(caddy)
                .contains("x-content-type-options \"nosniff\"")
                .contains("x-frame-options \"deny\"")
                .contains("content-security-policy")
                .doesNotContain("strict-transport-security");
    }

    @Test
    @DisplayName("REQ-WEB-005/006/012 - Caddy separates asset caching and overwrites trusted forwarding")
    void caddyShouldProtectStaticCachingAndForwardOnlyProxyOwnedValues() throws IOException {
        String caddy = requiredFile(PRODUCTION_CADDYFILE).toLowerCase();

        assertThat(caddy)
                .contains("path_regexp fingerprint")
                .contains("cache-control \"public, max-age=31536000, immutable\"")
                .contains("cache-control \"no-cache\"")
                .contains("header_up -x-forwarded-for")
                .contains("header_up -x-forwarded-host")
                .contains("header_up -x-forwarded-port")
                .contains("header_up -x-forwarded-proto")
                .contains("header_up -x-request-id")
                .contains("header_up x-forwarded-for")
                .contains("header_up x-forwarded-host")
                .contains("header_up x-forwarded-port")
                .contains("header_up x-forwarded-proto")
                .contains("header_up x-request-id")
                .doesNotContain("strict-transport-security");
    }

    @Test
    @DisplayName("REQ-OPS-009 - manifest records the complete rollback retention boundary")
    void releaseManifestShouldDeclareRollbackRetentionAndDatabasePolicy() throws IOException {
        String manifest = requiredFile(RELEASE_MANIFEST).toLowerCase();

        assertThat(manifest)
                .contains("format_version: 1")
                .contains("database_change:")
                .contains("database_rollback_compatible:")
                .contains("retain_backend_digest: true")
                .contains("retain_frontend_archive: true")
                .contains("retain_frontend_checksum: true")
                .contains("retain_manifest: true")
                .contains("retain_fingerprinted_assets: true");
        assertThat(integerValue(manifest, "minimum_days")).isGreaterThanOrEqualTo(14);
        assertThat(integerValue(manifest, "minimum_verified_releases")).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("REQ-OPS-008 and ADR-0028 - deployment requires and records explicit Developer approval")
    void releasePlaybookShouldGateMutationOnRecordedDeveloperApproval() throws IOException {
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();
        String preTasks = section(playbook, "pre_tasks:", "tasks:");
        String successRecord = section(playbook, "record release result", "rescue:");

        assertThat(preTasks).contains("developer").contains("approval");
        assertThat(successRecord).contains("approval");
    }

    @Test
    @DisplayName("REQ-OPS-012 - first-launch verification failure restores the commissioning gate")
    void releasePlaybookShouldReenableCommissioningAfterFirstLaunchFailure() throws IOException {
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();

        assertThat(playbook)
                .contains("re-enable commissioning gate after first-launch verification failure")
                .contains("first_launch | bool")
                .contains("gam_commissioning_enabled=true");
        assertOrdered(
                playbook,
                "verify production release",
                "re-enable commissioning gate after first-launch verification failure",
                "apply first-launch commissioning transition"
        );
    }

    @Test
    @DisplayName("REQ-OPS-008 - deployment lock is exclusive and released on every transaction path")
    void releasePlaybookShouldAcquireAndAlwaysReleaseTheDeploymentLock() throws IOException {
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();

        assertThat(playbook)
                .contains("deployment_lock_directory")
                .contains("mkdir")
                .contains("another production deployment")
                .contains("always:")
                .contains("release deployment lock");
        assertOrdered(
                playbook,
                "acquire exclusive deployment lock",
                "mark deployment lock as acquired",
                "release deployment lock"
        );
    }

    @Test
    @DisplayName("REQ-OPS-008 - fresh-backup and Flyway gates precede database-changing rollout")
    void releasePlaybookShouldGateMigrationOnBackupFreshnessAndFlywayValidation() throws IOException {
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();

        assertThat(playbook)
                .contains("backup_max_age_seconds")
                .contains("backup_marker.stat.mtime")
                .contains("database_change_required")
                .contains("flyway gate")
                .contains("apply migration and release sequence");
        assertOrdered(
                playbook,
                "enforce backup freshness before a database change",
                "flyway gate",
                "apply migration and release sequence"
        );
    }

    @Test
    @DisplayName("REQ-WEB-013 - deployment verifies release publication and immutability before transfer")
    void releasePlaybookShouldGateFrontendTransferOnImmutableReleaseMetadata() throws IOException {
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();

        assertThat(playbook)
                .contains("published")
                .contains("prerelease")
                .contains("immutable")
                .contains("sidecar")
                .contains("sha256sum");
        assertOrdered(
                playbook,
                "published",
                "sha256sum",
                "transfer the verified fixture frontend archive"
        );
    }

    @Test
    @DisplayName("REQ-OPS-008/009 - failed release records all pair coordinates before rollback")
    void failedReleaseRecordShouldRetainRollbackCoordinates() throws IOException {
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();
        String failedRecord = section(
                playbook,
                "record failed release result",
                "inspect previous compatible pair"
        );

        assertThat(failedRecord)
                .contains("backend_image")
                .contains("backend_release_commit")
                .contains("frontend_repository")
                .contains("frontend_tag")
                .contains("frontend_artifact")
                .contains("frontend_sha256")
                .contains("frontend_release_commit");
    }

    @Test
    @DisplayName("REQ-OPS-008/009 - rollout verifies proxy, backend, database, and public health before recording")
    void releasePlaybookShouldVerifyAllRuntimeBoundariesBeforeSuccessRecord() throws IOException {
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();

        assertThat(playbook)
                .contains("validate caddy configuration after rollout")
                .contains("validate")
                .contains("--config")
                .contains("/etc/caddy/caddyfile")
                .contains("pg_isready")
                .contains("verify private backend health")
                .contains("verify production release")
                .contains("/api/health")
                .contains("cache-control")
                .contains("status\":\"up\"");
        assertOrdered(
                playbook,
                "validate caddy configuration after rollout",
                "verify database connectivity",
                "verify private backend health",
                "verify production release",
                "record release result"
        );
    }

    @Test
    @DisplayName("REQ-OPS-008/009 - incompatible database changes retain maintenance and avoid downgrade")
    void rollbackShouldDistinguishCompatibleApplicationFailureFromDatabaseIncompatibility() throws IOException {
        String playbook = requiredFile(RELEASE_PLAYBOOK).toLowerCase();

        assertThat(playbook)
                .contains("database-compatible application rollback")
                .contains("disable maintenance response after compatible rollback")
                .contains("database-incompatible failure retains maintenance response")
                .contains("no automatic database downgrade");
    }

    private static String requiredFile(Path path) throws IOException {
        assertThat(Files.exists(path)).as("required production artifact: %s", path).isTrue();
        return Files.readString(path);
    }

    private static String scalar(String yaml, String key) {
        Matcher matcher = Pattern.compile(
                "(?m)^\\s*" + Pattern.quote(key) + ":\\s*([^#\\r\\n]+)"
        ).matcher(yaml);
        assertThat(matcher.find()).as("fixture manifest key: %s", key).isTrue();
        return matcher.group(1).trim();
    }

    private static String serviceBlock(String compose, String service) {
        Matcher matcher = Pattern.compile(
                "(?ms)^  " + Pattern.quote(service) + ":\\R(.*?)(?=^  [A-Za-z0-9_-]+:\\R|\\z)"
        ).matcher(compose);
        assertThat(matcher.find()).as("Compose service: %s", service).isTrue();
        return matcher.group();
    }

    private static String section(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertThat(start).as("section start: %s", startMarker).isGreaterThanOrEqualTo(0);
        assertThat(end).as("section end: %s", endMarker).isGreaterThan(start);
        return source.substring(start, end);
    }

    private static int integerValue(String yaml, String key) {
        Matcher matcher = Pattern.compile(
                "(?m)^\\s*" + Pattern.quote(key) + ":\\s*(\\d+)\\s*$"
        ).matcher(yaml);
        assertThat(matcher.find()).as("manifest integer: %s", key).isTrue();
        return Integer.parseInt(matcher.group(1));
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
