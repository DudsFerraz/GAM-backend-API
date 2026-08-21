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
import static org.junit.jupiter.api.Assertions.assertAll;

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
    private static final Path PRODUCTION_IDEMPOTENCY_CHECK = Path.of(
            "operations", "ansible", "idempotency-check.sh"
    );
    private static final Path PRODUCTION_INVENTORY = Path.of(
            "operations", "ansible", "inventory", "production.yml"
    );
    private static final Path PRODUCTION_RELEASE_PLAYBOOK = Path.of(
            "deploy", "production", "ansible", "deploy-release.yml"
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
        Matcher proxyMetricsEndpoint = Pattern.compile(
                "http://proxy\\.internal:(\\d+)/metrics"
        ).matcher(groupVars);

        assertThat(proxyMetricsEndpoint.find())
                .as("the proxy target must declare its private metrics listener port")
                .isTrue();
        String metricsPort = proxyMetricsEndpoint.group(1);

        assertThat(metricsPort)
                .as("the collector must not use Caddy's mutating administration API as its metrics endpoint")
                .isNotEqualTo("2019");
        assertThat(caddyfile)
                .containsPattern("(?m)^\\s*admin\\s+(?:off|localhost:2019|127\\.0\\.0\\.1:2019)\\s*$")
                .doesNotContainPattern("(?m)^\\s*admin\\s+(?:0\\.0\\.0\\.0|:2019)\\s*$")
                .containsPattern(
                        "(?ms)^\\s*:" + Pattern.quote(metricsPort)
                                + "\\s*\\{\\s*metrics(?:\\s+/metrics)?\\s*\\}\\s*$"
                );
        assertThat(caddyService)
                .contains("private:")
                .contains("aliases:")
                .contains("- proxy.internal")
                .doesNotContain("2019:2019");
        assertThat(groupVars)
                .containsPattern("(?m)^better_stack_proxy_target_host:.*proxy\\.internal.*$")
                .containsPattern(
                        "(?m)^better_stack_proxy_target_endpoint:.*http://proxy\\.internal:"
                                + Pattern.quote(metricsPort) + "/metrics.*$"
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
    @DisplayName("REQ-OPS-007/010 and ADR-0029 - clean-host provisioning -> PostgreSQL metrics prerequisites are idempotently reconciled")
    void postgresqlMetricsPrerequisitesShouldBeIdempotentlyReconciledOnACleanHost() throws IOException {
        String compose = requiredFile(PRODUCTION_COMPOSE).toLowerCase();
        String playbook = requiredFile(PRODUCTION_PLAYBOOK).toLowerCase();
        String postgresService = section(compose, "\n  postgres:\n", "\n  backend:\n");

        boolean idempotentRoleCreation = playbook.contains("community.postgresql.postgresql_user:")
                && playbook.contains("state: present")
                || playbook.contains("create role")
                && playbook.contains("pg_roles")
                && playbook.contains("if not exists");
        boolean pgMonitorGrant = playbook.contains("grant pg_monitor to")
                || playbook.contains("community.postgresql.postgresql_membership:")
                && playbook.contains("pg_monitor");
        boolean databaseConnectGrant = playbook.contains("grant connect on database")
                || playbook.contains("community.postgresql.postgresql_privs:")
                && playbook.contains("privs: connect");
        boolean idempotentExtensionCreation = playbook.contains("create extension if not exists pg_stat_statements")
                || playbook.contains("community.postgresql.postgresql_ext:")
                && playbook.contains("name: pg_stat_statements")
                && playbook.contains("state: present");

        assertThat(postgresService)
                .containsPattern("(?m)^\\s*-\\s*shared_preload_libraries=pg_stat_statements\\s*$");
        assertThat(playbook).contains("better_stack_postgresql_target_username");
        assertThat(idempotentRoleCreation)
                .as("the monitoring login role must be created idempotently on a clean database")
                .isTrue();
        assertThat(pgMonitorGrant)
                .as("the monitoring role must receive PostgreSQL's pg_monitor permissions")
                .isTrue();
        assertThat(databaseConnectGrant)
                .as("the monitoring role must receive CONNECT access to the monitored database")
                .isTrue();
        assertThat(idempotentExtensionCreation)
                .as("pg_stat_statements must be installed idempotently in the monitored database")
                .isTrue();
    }

    @Test
    @DisplayName("REQ-OPS-008/010 - clean-host monitoring baseline -> approved release converges every monitored service first")
    void cleanHostMonitoringBaselineShouldUseApprovedFullServiceDeployment() throws IOException {
        String playbook = requiredFile(PRODUCTION_PLAYBOOK).toLowerCase();
        String idempotencyCheck = requiredFile(PRODUCTION_IDEMPOTENCY_CHECK).toLowerCase();
        String releasePlaybook = requiredFile(PRODUCTION_RELEASE_PLAYBOOK).toLowerCase();
        String compose = requiredFile(PRODUCTION_COMPOSE).toLowerCase();
        int postgresqlDiscovery = playbook.indexOf(
                "discover the running production postgresql container"
        );
        int firstFullSiteApply = idempotencyCheck.indexOf("site.yml");

        assertThat(postgresqlDiscovery)
                .as("the PostgreSQL monitoring reconciliation entry point must exist")
                .isGreaterThanOrEqualTo(0);
        assertThat(firstFullSiteApply)
                .as("the clean-host idempotency path must invoke the complete production site")
                .isGreaterThanOrEqualTo(0);
        String beforePostgresqlDiscovery = playbook.substring(0, postgresqlDiscovery);
        String beforeFullSiteApply = idempotencyCheck.substring(0, firstFullSiteApply);
        boolean approvedReleaseRunsBeforeMonitoring = beforePostgresqlDiscovery.contains(
                "deploy-release.yml"
        ) || beforeFullSiteApply.contains("deploy-release.yml");
        String rollout = section(
                releasePlaybook,
                "\n        - name: apply migration and release sequence\n",
                "\n        - name: mark a database migration as applied when this pair changes schema\n"
        );
        String backendService = section(compose, "\n  backend:\n", "\nvolumes:\n");

        assertThat(approvedReleaseRunsBeforeMonitoring)
                .as(
                        "clean-host monitoring must follow the approved release workflow instead of starting only PostgreSQL from the placeholder Compose file"
                )
                .isTrue();
        assertThat(releasePlaybook)
                .contains("developer_approval")
                .contains("selected_release.backend.image")
                .contains("selected_release.frontend");
        assertThat(rollout)
                .contains("- backend")
                .contains("- caddy");
        assertThat(backendService)
                .contains("depends_on:")
                .contains("postgres:")
                .contains("condition: service_healthy");
    }

    @Test
    @DisplayName("REQ-OPS-008 and ADR-0028 - production release inputs -> authenticated immutable GitHub Release acquisition")
    void productionReleaseInputsShouldComeFromAnAuthenticatedImmutableGithubRelease() throws IOException {
        String releasePlaybook = requiredFile(PRODUCTION_RELEASE_PLAYBOOK).toLowerCase();
        String idempotencyCheck = requiredFile(PRODUCTION_IDEMPOTENCY_CHECK).toLowerCase();
        String productionInputContract = releasePlaybook + "\n" + idempotencyCheck;
        boolean downloadsGithubReleaseOnController = Pattern.compile(
                "(?s)(?:gh\\s+release\\s+download|-\\s*gh\\s*\\n\\s*-\\s*release\\s*\\n\\s*-\\s*download)"
                        + ".{0,2000}?delegate_to:\\s*localhost"
        ).matcher(productionInputContract).find();

        assertAll(
                () -> assertThat(releasePlaybook)
                        .as("repository test resources must not be the production artifact source")
                        .doesNotContain("src/test/resources/production/fixtures")
                        .doesNotContain("frontend_fixture_root"),
                () -> assertThat(downloadsGithubReleaseOnController)
                        .as("the controller must download the selected immutable GitHub Release assets outside KVM 2")
                        .isTrue(),
                () -> assertThat(productionInputContract)
                        .as("authenticated GitHub CLI access must use externally custodied token input")
                        .containsPattern("(?i)(?:gh_token|github_token)"),
                () -> assertThat(productionInputContract)
                        .contains("selected_release.frontend.repository")
                        .contains("selected_release.frontend.tag")
                        .contains("selected_release.frontend.artifact")
                        .contains("frontend_checksum_source")
        );
    }

    @Test
    @DisplayName("REQ-WEB-002 and REQ-OPS-007/008 - all production plays share the canonical GAM_PUBLIC_ORIGIN")
    void productionComponentsShouldShareTheSingleCanonicalPublicOrigin() throws IOException {
        String groupVars = requiredFile(PRODUCTION_GROUP_VARS).toLowerCase();
        String site = requiredFile(PRODUCTION_PLAYBOOK).toLowerCase();
        String releasePlaybook = requiredFile(PRODUCTION_RELEASE_PLAYBOOK).toLowerCase();
        String compose = requiredFile(PRODUCTION_COMPOSE).toLowerCase();
        String caddyfile = requiredFile(PRODUCTION_CADDYFILE).toLowerCase();
        String productionContract = groupVars + site + releasePlaybook + compose + caddyfile;

        assertThat(productionContract)
                .as("production must not introduce a divergent public-origin environment variable")
                .doesNotContain("gam_production_origin");
        assertThat(groupVars)
                .containsPattern(
                        "(?m)^gam_public_origin:\\s*[\"']?\\{\\{\\s*lookup\\([\"']env[\"'],\\s*[\"']gam_public_origin[\"']\\)\\s*}}[\"']?\\s*$"
                );
        assertThat(site)
                .as("Better Stack availability and TLS monitoring must use the canonical origin variable")
                .contains("gam_public_origin + '/api/health'")
                .contains("equalto', gam_public_origin")
                .doesNotContain("production_origin");
        assertThat(releasePlaybook)
                .contains("lookup('env', 'gam_public_origin')")
                .contains("{{ gam_public_origin }}/api/health");
        assertThat(compose)
                .contains("gam_public_origin: ${gam_public_origin:?gam_public_origin must be set}");
        assertThat(caddyfile).contains("{$gam_public_origin}");
    }

    @Test
    @DisplayName("REQ-OPS-010 - real full-site replay -> only one explained audit append is permitted")
    void realReplayShouldExposeReleaseChangesAndRejectOnlyUnexplainedChanges() throws IOException {
        String idempotencyCheck = requiredFile(PRODUCTION_IDEMPOTENCY_CHECK).toLowerCase();
        String site = requiredFile(PRODUCTION_PLAYBOOK).toLowerCase();
        String releasePlaybook = requiredFile(PRODUCTION_RELEASE_PLAYBOOK).toLowerCase();
        String replayInvocation = realReplayInvocation(idempotencyCheck);

        assertThat(site).contains("deploy-release.yml");
        assertThat(releasePlaybook)
                .contains("acquire exclusive deployment lock")
                .contains("retain the previous compatible pair")
                .contains("inspect previous compatible pair for rollback");
        assertThat(replayInvocation)
                .contains("site.yml")
                .contains("--diff")
                .doesNotContain("--check")
                .doesNotContain("--start-at-task");
        assertReplayAcceptsOnlyOneExplainedAuditAppend(idempotencyCheck);
    }

    @Test
    @DisplayName("REQ-OPS-008/010 - real replay -> archive digests are recomputed before integrity assertions")
    void realReplayShouldRecomputeArchiveDigestsBeforeAssertions() throws IOException {
        String releasePlaybook = requiredFile(PRODUCTION_RELEASE_PLAYBOOK).toLowerCase();
        String controllerDownloadedArchiveDigest = section(
                releasePlaybook,
                "\n        - name: compute the controller-downloaded frontend archive digest\n",
                "\n        - name: verify frontend release metadata and archive checksum bytes\n"
        );
        String controllerDownloadedArchiveDigestAssertion = section(
                releasePlaybook,
                "\n        - name: verify frontend release metadata and archive checksum\n",
                "\n        - name: verify frontend archive structure before transfer\n"
        );
        String transferredDigest = section(
                releasePlaybook,
                "\n        - name: verify the transferred frontend archive again\n",
                "\n        - name: assert transferred frontend archive remains immutable\n"
        );
        String transferredDigestAssertion = section(
                releasePlaybook,
                "\n        - name: assert transferred frontend archive remains immutable\n",
                "\n        - name: read current commissioning gate state before maintenance\n"
        );

        assertThat(releasePlaybook)
                .as("production task labels must describe controller-downloaded immutable release assets")
                .doesNotContain("fixture frontend archive")
                .contains("transfer the verified controller-downloaded frontend archive");
        assertThat(controllerDownloadedArchiveDigestAssertion)
                .contains("frontend_archive_digest.stdout.split()[0]");
        assertThat(transferredDigestAssertion)
                .contains("transferred_frontend_digest.stdout.split()[0]");
        assertAll(
                () -> assertThat(controllerDownloadedArchiveDigest)
                        .as(
                                "the controller-side digest must be recomputed without reporting a configuration change"
                        )
                        .contains("sha256sum")
                        .containsPattern("(?m)^\\s*changed_when:\\s*false\\s*$"),
                () -> assertThat(transferredDigest)
                        .as(
                                "the transferred digest must be recomputed without reporting a configuration change"
                        )
                        .contains("sha256sum")
                        .containsPattern("(?m)^\\s*changed_when:\\s*false\\s*$")
        );
    }

    @Test
    @DisplayName("REQ-OPS-010 - post-bootstrap full-site play -> connects as the steady-state gamops user")
    void postBootstrapFullSitePlayShouldUseTheSteadyStateOperationsUser() throws IOException {
        String playbook = requiredFile(PRODUCTION_PLAYBOOK).toLowerCase();
        String inventory = requiredFile(PRODUCTION_INVENTORY).toLowerCase();
        int productionPlayStart = playbook.indexOf(
                "\n- name: provision gam production backup, recovery, aws, and monitoring behavior\n"
        );

        assertThat(inventory)
                .containsPattern("(?m)^\\s*steady_state_user:\\s*gamops\\s*$");
        assertThat(productionPlayStart)
                .as("the post-bootstrap production play must exist")
                .isGreaterThanOrEqualTo(0);
        String productionPlay = playbook.substring(productionPlayStart);
        assertThat(productionPlay)
                .containsPattern(
                        "(?m)^\\s*remote_user:\\s*[\"']?\\{\\{\\s*steady_state_user\\s*}}[\"']?\\s*$"
                );
    }

    @Test
    @DisplayName("REQ-OPS-010 - real apply and replay -> PostgreSQL monitoring state is verified")
    void idempotencyCheckShouldApplyReplayAndVerifyPostgresqlMonitoringState() throws IOException {
        String idempotencyCheck = requiredFile(PRODUCTION_IDEMPOTENCY_CHECK).toLowerCase();

        assertThat(countOccurrences(idempotencyCheck, "site.yml"))
                .as("the complete production site, including monitoring, must be applied and replayed")
                .isGreaterThanOrEqualTo(2);
        assertThat(idempotencyCheck)
                .contains("pg_roles")
                .containsPattern("(?s)(?:pg_auth_members|pg_has_role).*pg_monitor")
                .contains("has_database_privilege")
                .contains("pg_extension")
                .contains("pg_stat_statements")
                .contains("shared_preload_libraries");
        assertReplayAcceptsOnlyOneExplainedAuditAppend(idempotencyCheck);
        assertThat(realReplayInvocation(idempotencyCheck))
                .contains("site.yml")
                .doesNotContain("--check");
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

    private static String realReplayInvocation(String script) {
        int firstInvocation = script.indexOf("ansible-playbook");
        int replayInvocation = script.indexOf("ansible-playbook", firstInvocation + 1);
        int replayEnd = script.indexOf("2>&1 | tee \"$replay_log\"", replayInvocation);

        assertThat(firstInvocation).as("the real full-site apply must exist").isGreaterThanOrEqualTo(0);
        assertThat(replayInvocation).as("the real full-site replay must exist").isGreaterThan(firstInvocation);
        assertThat(replayEnd).as("the replay output must be captured for change classification").isGreaterThan(replayInvocation);
        return script.substring(replayInvocation, replayEnd);
    }

    private static void assertReplayAcceptsOnlyOneExplainedAuditAppend(String script) {
        assertThat(script)
                .containsPattern("(?m)^\\s*if \\(\\(replay_changed_count\\s*>\\s*0\\)\\); then\\s*$")
                .contains("record successful same-release convergence")
                .contains("explained_audit_changes")
                .containsPattern("(?s)unexplained_changes\\s*>\\s*0.*unexplained configuration drift")
                .containsPattern(
                        "(?s)replay_changed_count\\s*!=\\s*1\\s*\\|\\|\\s*explained_audit_changes\\s*!=\\s*1"
                )
                .contains("outside the single same-release audit append");
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
