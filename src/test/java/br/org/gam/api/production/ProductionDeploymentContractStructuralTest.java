package br.org.gam.api.production;

import br.org.gam.api.testing.annotation.StructuralTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@StructuralTest
@DisplayName("Structure - production deployment contracts")
class ProductionDeploymentContractStructuralTest {

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

    @Test
    @DisplayName("REQ-OPS-001/002 and ADR-0024 - only Caddy publishes web ports")
    void productionComposeShouldKeepApplicationAndDatabasePrivate() throws IOException {
        String compose = requiredFile(PRODUCTION_COMPOSE);
        List<String> services = serviceBlocks(compose);

        assertThat(services).as("production Compose services").isNotEmpty();

        String caddy = serviceWithImage(services, "caddy");
        assertThat(caddy)
                .contains("ports:")
                .contains("\"80:80\"")
                .contains("\"443:443\"");

        String postgres = serviceWithImage(services, "postgres");
        assertThat(postgres)
                .containsPattern("(?m)^\\s*image:\\s+postgres:18(?:[-._A-Za-z0-9]+)?\\s*$")
                .doesNotContain("ports:");

        String backend = services.stream()
                .filter(service -> !service.equals(caddy) && !service.equals(postgres))
                .filter(service -> service.contains("image:"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("backend service is missing"));
        assertThat(backend)
                .containsPattern("(?m)^\\s*image:\\s+\\S+@sha256:[0-9a-f]{64}\\s*$")
                .contains("networks:")
                .doesNotContain("ports:");

        assertThat(caddy).contains("frontend").contains(":ro");
        assertThat(compose).contains("networks:").contains("volumes:");
    }

    @Test
    @DisplayName("REQ-OPS-012 and ADR-0028 - commissioning closes the default route")
    void caddyShouldProvideDefaultCommissioningAndMaintenanceResponses() throws IOException {
        String caddy = requiredFile(PRODUCTION_CADDYFILE);
        String normalized = caddy.toLowerCase();

        assertThat(normalized)
                .contains("commissioning")
                .contains("enabled")
                .contains("allowlist")
                .contains("503")
                .contains("cache-control")
                .contains("no-store")
                .contains("maintenance")
                .doesNotContain("basic_auth");
        assertThat(normalized).contains("reverse_proxy").contains("/api/*");
        assertThat(normalized).contains("redir").contains("https://");
        assertThat(normalized).contains("try_files").contains("index.html");
        assertThat(normalized).contains("default").contains("true");
    }

    @Test
    @DisplayName("REQ-WEB-011/013 and REQ-OPS-009 - manifest pins the rollback pair")
    void releaseManifestShouldPinImmutableArtifactIdentity() throws IOException {
        String manifest = requiredFile(RELEASE_MANIFEST);
        String normalized = manifest.toLowerCase();

        assertThat(normalized)
                .contains("frontend:")
                .contains("repository:")
                .contains("tag:")
                .contains("artifact:")
                .contains("sha256:")
                .contains("backend:")
                .contains("image:")
                .contains("release_commit")
                .contains("previous")
                .doesNotContain("latest");
        assertThat(manifest)
                .containsPattern("(?m)^\\s*sha256:\\s+[0-9a-f]{64}\\s*$")
                .containsPattern("(?m)^\\s*image:\\s+\\S+@sha256:[0-9a-f]{64}\\s*$");
    }

    @Test
    @DisplayName("REQ-OPS-008/009 - release playbook gates, verifies, records, and rolls back safely")
    void releasePlaybookShouldOrderGatesVerificationRecordingAndRollback() throws IOException {
        String playbook = requiredFile(RELEASE_PLAYBOOK);
        String normalized = playbook.toLowerCase();

        assertThat(normalized)
                .contains("exclusive deployment lock")
                .contains("backup freshness")
                .contains("flyway")
                .contains("enable maintenance response")
                .contains("verify production release")
                .contains("record release result")
                .contains("release deployment lock")
                .contains("database-compatible")
                .contains("database-incompatible");

        assertOrdered(
                normalized,
                "exclusive deployment lock",
                "backup freshness",
                "flyway",
                "enable maintenance response",
                "verify production release",
                "record release result",
                "release deployment lock"
        );
        assertThat(normalized)
                .contains("migration")
                .contains("health")
                .contains("current release manifest")
                .contains("previous compatible");
    }

    private static String requiredFile(Path path) throws IOException {
        assertThat(Files.exists(path)).as("required production artifact: %s", path).isTrue();
        return Files.readString(path);
    }

    private static List<String> serviceBlocks(String compose) {
        Pattern servicePattern = Pattern.compile(
                "(?ms)^  [A-Za-z0-9_-]+:\\R.*?(?=^  [A-Za-z0-9_-]+:\\R|\\z)"
        );
        Matcher matcher = servicePattern.matcher(compose);
        List<String> services = new ArrayList<>();
        while (matcher.find()) {
            services.add(matcher.group());
        }
        return services;
    }

    private static String serviceWithImage(List<String> services, String imageName) {
        return services.stream()
                .filter(service -> service.toLowerCase().contains("image:"))
                .filter(service -> service.toLowerCase().contains(imageName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(imageName + " service is missing"));
    }

    private static void assertOrdered(String source, String... markers) {
        int previous = -1;
        for (String marker : markers) {
            int current = source.indexOf(marker);
            assertThat(current).as("marker '%s' in release playbook", marker).isGreaterThan(previous);
            previous = current;
        }
    }
}
