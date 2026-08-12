package br.org.gam.api.production;

import br.org.gam.api.testing.annotation.StructuralTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@StructuralTest
@DisplayName("Structure - production service metrics are real and collector-reachable")
class ProductionServiceMetricsStructuralTest {

    private static final Path PRODUCTION_COMPOSE = Path.of(
            "deploy", "production", "compose.yml"
    );
    private static final Path PRODUCTION_CADDYFILE = Path.of(
            "deploy", "production", "Caddyfile"
    );
    private static final Path PRODUCTION_GROUP_VARS = Path.of(
            "operations", "ansible", "group_vars", "production.yml"
    );
    private static final Path PRODUCTION_PLAYBOOK = Path.of(
            "operations", "ansible", "site.yml"
    );
    private static final Path APPLICATION_PROPERTIES = Path.of(
            "src", "main", "resources", "application.properties"
    );
    private static final Path POM = Path.of("pom.xml");

    @Test
    @DisplayName("REQ-OPS-007/010 and ADR-0029 - proxy metrics target resolves to Caddy's private metrics listener")
    void proxyMetricsTargetShouldResolveToAnEnabledPrivateCaddyListener() throws IOException {
        String compose = requiredFile(PRODUCTION_COMPOSE).toLowerCase();
        String caddyfile = requiredFile(PRODUCTION_CADDYFILE).toLowerCase();
        String groupVars = requiredFile(PRODUCTION_GROUP_VARS).toLowerCase();
        String caddyService = section(compose, "\n  caddy:\n", "\n  postgres:\n");

        assertThat(caddyfile)
                .containsPattern("(?m)^\\s*admin\\s+(?:0\\.0\\.0\\.0)?:2019\\s*$")
                .containsPattern("(?m)^\\s*metrics(?:\\s*\\{)?\\s*$");
        assertThat(caddyService)
                .contains("private:")
                .contains("aliases:")
                .contains("- proxy.internal")
                .doesNotContain("2019:2019");
        assertThat(groupVars)
                .containsPattern("(?m)^better_stack_proxy_target_host:.*proxy\\.internal.*$")
                .containsPattern(
                        "(?m)^better_stack_proxy_target_endpoint:.*http://proxy\\.internal:2019/metrics.*$"
                );
    }

    @Test
    @DisplayName("REQ-OPS-007/010 and ADR-0029 - backend target resolves to an enabled private Prometheus endpoint")
    void backendMetricsTargetShouldResolveToAnEnabledPrivatePrometheusEndpoint() throws IOException {
        String compose = requiredFile(PRODUCTION_COMPOSE).toLowerCase();
        String caddyfile = requiredFile(PRODUCTION_CADDYFILE).toLowerCase();
        String groupVars = requiredFile(PRODUCTION_GROUP_VARS).toLowerCase();
        String properties = requiredFile(APPLICATION_PROPERTIES).toLowerCase();
        String pom = requiredFile(POM).toLowerCase();
        String backendService = section(compose, "\n  backend:\n", "\nvolumes:\n");
        String managementBlock = section(
                caddyfile,
                "\n        handle @management {\n",
                "\n        handle @api {\n"
        );

        assertThat(pom)
                .contains("<artifactid>spring-boot-starter-actuator</artifactid>")
                .contains("<artifactid>micrometer-registry-prometheus</artifactid>");
        assertThat(properties).containsPattern(
                "(?m)^management\\.endpoints\\.web\\.exposure\\.include\\s*=.*\\bprometheus\\b.*$"
        );
        assertThat(caddyfile)
                .contains("@management path /api/actuator /api/actuator/*");
        assertThat(managementBlock)
                .contains("respond 404");
        assertThat(backendService)
                .contains("private:")
                .contains("aliases:")
                .contains("- backend.internal")
                .doesNotContain("8080:8080");
        assertThat(groupVars)
                .containsPattern("(?m)^better_stack_backend_target_host:.*backend\\.internal.*$")
                .containsPattern(
                        "(?m)^better_stack_backend_target_endpoint:.*http://backend\\.internal:8080/actuator/prometheus.*$"
                );
    }

    @Test
    @DisplayName("REQ-OPS-002/007/010 and ADR-0029 - collector reaches all service targets only through GAM's internal network")
    void collectorShouldReachServiceTargetsThroughTheInternalProductionNetwork() throws IOException {
        String compose = requiredFile(PRODUCTION_COMPOSE).toLowerCase();
        String groupVars = requiredFile(PRODUCTION_GROUP_VARS).toLowerCase();
        String playbook = requiredFile(PRODUCTION_PLAYBOOK).toLowerCase();
        String postgresService = section(compose, "\n  postgres:\n", "\n  backend:\n");
        String privateNetwork = compose.substring(compose.indexOf("\nnetworks:\n"));

        assertThat(playbook)
                .contains("label=com.docker.compose.project=better-stack-collector")
                .containsPattern(
                        "(?s)docker\\s*\\n\\s*-\\s*network\\s*\\n\\s*-\\s*connect"
                )
                .contains("gam-production_private")
                .contains("label=com.docker.compose.service=collector")
                .contains("docker\n          - inspect")
                .contains("networksettings.networks")
                .contains("'gam-production_private' not in better_stack_collector_networks.stdout");
        assertThat(postgresService)
                .contains("private:")
                .contains("aliases:")
                .contains("- postgres.internal")
                .doesNotContain("5432:5432");
        assertThat(privateNetwork)
                .contains("private:")
                .contains("internal: true");
        assertThat(groupVars)
                .containsPattern("(?m)^better_stack_postgresql_target_host:.*postgres\\.internal.*$")
                .containsPattern("(?m)^better_stack_postgresql_target_port:.*5432.*$");
    }

    @Test
    @DisplayName("REQ-OPS-007 and ADR-0029 - PostgreSQL target transport matches the production database listener")
    void postgresqlTargetTransportShouldMatchTheProductionDatabaseListener() throws IOException {
        String compose = requiredFile(PRODUCTION_COMPOSE).toLowerCase();
        String groupVars = requiredFile(PRODUCTION_GROUP_VARS).toLowerCase();
        String playbook = requiredFile(PRODUCTION_PLAYBOOK).toLowerCase();
        String postgresService = section(compose, "\n  postgres:\n", "\n  backend:\n");

        boolean targetUsesPlainPrivateTransport = playbook.contains("ssl_mode: disable")
                && !playbook.contains("ssl_mode: require");
        boolean postgresProvidesTls = postgresService.contains("ssl=on")
                && postgresService.contains("ssl_cert_file")
                && postgresService.contains("ssl_key_file");

        assertThat(targetUsesPlainPrivateTransport || postgresProvidesTls)
                .as(
                        "the Better Stack PostgreSQL target must not require TLS unless the production PostgreSQL listener configures a certificate and key"
                )
                .isTrue();
        assertThat(postgresService)
                .contains("./secrets/postgres-tls/server.crt:/run/gam-postgres-tls/server.crt:ro")
                .contains("./secrets/postgres-tls/server.key:/run/gam-postgres-tls/server.key:ro");
        assertThat(groupVars)
                .contains("postgres_tls_directory: /opt/gam/secrets/postgres-tls")
                .contains("postgres_container_group_id: 70");
        assertThat(playbook)
                .contains("verify the postgresql tls certificate and private key match")
                .contains("mode: \"0640\"");
    }

    @Test
    @DisplayName("REQ-OPS-007/010 and ADR-0029 - PostgreSQL metrics target authenticates with externally custodied credentials")
    void postgresqlTargetShouldUseExternallyCustodiedCredentials() throws IOException {
        String groupVars = requiredFile(PRODUCTION_GROUP_VARS).toLowerCase();
        String playbook = requiredFile(PRODUCTION_PLAYBOOK).toLowerCase();

        assertThat(groupVars)
                .containsPattern(
                        "(?m)^better_stack_postgresql_target_username:.*lookup\\('env'.*better_stack_postgresql_target_username.*$"
                )
                .containsPattern(
                        "(?m)^better_stack_postgresql_target_password:.*lookup\\('env'.*better_stack_postgresql_target_password.*$"
                );
        assertThat(playbook)
                .contains("better_stack_postgresql_target_username | length > 0")
                .contains("better_stack_postgresql_target_password | length > 0");
        assertThat(countOccurrences(
                playbook,
                "username: \"{{ better_stack_postgresql_target_username }}\""
        )).isEqualTo(2);
        assertThat(countOccurrences(
                playbook,
                "password: \"{{ better_stack_postgresql_target_password }}\""
        )).isEqualTo(2);
    }

    @Test
    @DisplayName("REQ-OPS-007 and ADR-0029 - collector acceptance verifies every service target is collecting")
    void collectorAcceptanceShouldVerifyEveryServiceTargetAfterNetworkAttachment() throws IOException {
        String playbook = requiredFile(PRODUCTION_PLAYBOOK).toLowerCase();
        int networkAttachment = playbook.indexOf(
                "connect the better stack collector to the private production network"
        );

        assertThat(networkAttachment)
                .as("collector network attachment task must exist")
                .isGreaterThanOrEqualTo(0);

        String postAttachmentAcceptance = playbook.substring(networkAttachment);
        assertThat(postAttachmentAcceptance)
                .contains("/api/v1/collectors/{{ better_stack_collector_id }}/targets")
                .contains("attributes.status")
                .contains("equalto', 'up")
                .contains("better_stack_proxy_target_service")
                .contains("better_stack_backend_target_service")
                .contains("better_stack_postgresql_target_host");
    }

    private static String requiredFile(Path path) throws IOException {
        assertThat(path).exists().isRegularFile();
        return Files.readString(path).replace("\r\n", "\n");
    }

    private static String section(String text, String startMarker, String endMarker) {
        int start = text.indexOf(startMarker);
        int end = text.indexOf(endMarker, start + startMarker.length());

        assertThat(start)
                .as("start marker %s must exist", startMarker.trim())
                .isGreaterThanOrEqualTo(0);
        assertThat(end)
                .as("end marker %s must exist after %s", endMarker.trim(), startMarker.trim())
                .isGreaterThan(start);
        return text.substring(start, end);
    }

    private static int countOccurrences(String text, String value) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(value, index)) >= 0) {
            count++;
            index += value.length();
        }
        return count;
    }
}
