package br.org.gam.api.persistence;

import br.org.gam.api.db.migration.R__SeedOratorioGamLocation;
import br.org.gam.api.db.migration.R__SeedPermissionsAndRoles;
import br.org.gam.api.security.SecurityConfig;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.PersistenceTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@FunctionalTest
@IntegrationTest
@PersistenceTest
@DisplayName("Persistence - development fixture policy and dataset")
class DevelopmentFixtureMigrationPersistenceIT {

    static final String EXECUTION_MARKER_PLACEHOLDER = "gamDevFixtureExecutionEnabled";
    static final String PASSWORD_HASH_PLACEHOLDER = "gamDevFixturePasswordHash";

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final Set<String> CANONICAL_PERSONA_EMAILS = Set.of(
            "dev.sudo@example.com",
            "mariana.coord@example.com",
            "rafael.coord.sandbox@example.com",
            "camila.oratorio@example.com",
            "lucas.member@example.com",
            "helena.inactive@example.com",
            "beatriz.registration@example.com",
            "fernanda.solicitation@example.com",
            "joao.approval@example.com",
            "aline.rejection@example.com",
            "paulo.custom-role@example.com",
            "renata.custom-role@example.com"
    );
    private static final Map<String, Set<String>> ACCEPTED_PERSONA_ROLES = Map.ofEntries(
            Map.entry("dev.sudo@example.com", Set.of("SUDO")),
            Map.entry("mariana.coord@example.com", Set.of("COORD", "MEMBER")),
            Map.entry("rafael.coord.sandbox@example.com", Set.of("COORD", "MEMBER")),
            Map.entry("camila.oratorio@example.com", Set.of("MEMBER", "ORATORIO_COORD")),
            Map.entry("lucas.member@example.com", Set.of("MEMBER")),
            Map.entry("helena.inactive@example.com", Set.of("VISITOR")),
            Map.entry("beatriz.registration@example.com", Set.of()),
            Map.entry("fernanda.solicitation@example.com", Set.of()),
            Map.entry("joao.approval@example.com", Set.of()),
            Map.entry("aline.rejection@example.com", Set.of()),
            Map.entry("paulo.custom-role@example.com", Set.of()),
            Map.entry("renata.custom-role@example.com", Set.of("EVENT_SUPPORT"))
    );
    private static final Set<String> EVENT_SUPPORT_PERMISSIONS = Set.of(
            "EVENT_SEARCH",
            "EVENT_GET_MEMBER",
            "EVENT_GET_PRESENCES",
            "GAM_LOCATION_GET"
    );
    private static final String VALID_PASSWORD = UUID.randomUUID().toString();
    private static final String VALID_PASSWORD_HASH = passwordEncoder().encode(VALID_PASSWORD);

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine")
    );

    static {
        POSTGRES.start();
    }

    private final DataSource dataSource = dataSource();
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

    @Test
    @DisplayName("REQ-DEV-FIXTURE-001 - default migration path -> canonical fixture Accounts are excluded")
    void defaultMigrationPathShouldExcludeCanonicalFixtureAccounts() {
        String schema = uniqueSchema("dev_fixture_default_path");

        migrate(schema, Map.of(), false).migrate();

        assertThat(canonicalAccountCount(schema)).isZero();
        assertThat(tableCount(schema, "accounts")).isZero();
        assertThat(activeRoleCount(schema, "EVENT_SUPPORT")).isZero();
        assertThat(tableCount(schema, "events")).isZero();
        assertThat(tableCount(schema, "oratoriano_form_attachments")).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidConfiguration")
    @DisplayName("REQ-DEV-FIXTURE-001 and REQ-DEV-FIXTURE-002 - invalid execution input -> fail before fixture mutation")
    void invalidExecutionInputShouldFailBeforeFixtureMutation(
            String scenario,
            Map<String, String> placeholders
    ) {
        String schema = uniqueSchema("dev_fixture_fail_closed");

        assertThatThrownBy(() -> migrate(schema, placeholders, true).migrate())
                .isInstanceOf(FlywayException.class);
        assertThat(canonicalAccountCountWhenTableExists(schema)).isZero();
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-006 to REQ-DEV-FIXTURE-009 - fresh database -> canonical personas and useful scale")
    void freshDatabaseShouldReceiveCanonicalPersonasAndUsefulScale() {
        String schema = uniqueSchema("dev_fixture_personas");

        migrate(schema, validConfiguration(), true).migrate();

        assertThat(canonicalAccountIds(schema))
                .hasSize(CANONICAL_PERSONA_EMAILS.size())
                .containsOnlyKeys(CANONICAL_PERSONA_EMAILS);
        assertThat(jdbcTemplate.queryForList(
                "SELECT DISTINCT password_hash FROM " + schema + ".accounts WHERE email IN ("
                        + canonicalPersonaParameters() + ")",
                String.class,
                CANONICAL_PERSONA_EMAILS.toArray()
        )).containsExactly(VALID_PASSWORD_HASH);
        assertThat(passwordEncoder().matches(VALID_PASSWORD, VALID_PASSWORD_HASH)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".accounts "
                        + "WHERE deleted_at IS NULL AND email NOT LIKE '%@example.com'",
                Long.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT password_hash) FROM " + schema + ".accounts",
                Long.class
        )).isEqualTo(1);

        ACCEPTED_PERSONA_ROLES.forEach((email, expectedRoles) ->
                assertThat(activeRoles(schema, email))
                        .as("active Role projection for %s", email)
                        .containsExactlyInAnyOrderElementsOf(expectedRoles)
        );
        assertThat(activePermissions(schema, "EVENT_SUPPORT"))
                .containsExactlyInAnyOrderElementsOf(EVENT_SUPPORT_PERMISSIONS);
        assertThat(activeRoleCount(schema, "ARCHIVED_EVENT_SUPPORT")).isZero();
        assertThat(softDeletedRoleCount(schema, "ARCHIVED_EVENT_SUPPORT")).isEqualTo(1);

        assertThat(memberCount(schema, "ACTIVE")).isGreaterThanOrEqualTo(60);
        assertThat(memberCount(schema, "INACTIVE")).isGreaterThanOrEqualTo(2);
        assertThat(nonDeletedOratorianoCount(schema)).isGreaterThanOrEqualTo(60);
        assertThat(softDeletedOratorianoCount(schema)).isGreaterThanOrEqualTo(1);
        assertThat(solicitationCount(schema, "PENDING")).isGreaterThanOrEqualTo(2);
        assertThat(solicitationCount(schema, "APPROVED")).isGreaterThanOrEqualTo(1);
        assertThat(solicitationCount(schema, "REJECTED")).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-003 and REQ-DEV-FIXTURE-004 - fresh databases and rerun -> stable UUID v7 manifest")
    void canonicalManifestShouldUseStableUuidV7AcrossFreshDatabasesAndRerun() {
        String firstSchema = uniqueSchema("dev_fixture_identity_first");
        String secondSchema = uniqueSchema("dev_fixture_identity_second");
        Map<String, String> configuration = validConfiguration();

        migrate(firstSchema, configuration, true).migrate();
        migrate(secondSchema, configuration, true).migrate();

        Map<String, UUID> firstAccountIds = canonicalAccountIds(firstSchema);
        Map<String, UUID> secondAccountIds = canonicalAccountIds(secondSchema);
        assertThat(firstAccountIds).hasSize(CANONICAL_PERSONA_EMAILS.size());
        assertThat(firstAccountIds).isEqualTo(secondAccountIds);
        assertThat(firstAccountIds.values()).allMatch(id -> id.version() == 7);

        Map<String, UUID> firstRelationshipIds = canonicalRoleAssignmentIds(firstSchema);
        Map<String, UUID> secondRelationshipIds = canonicalRoleAssignmentIds(secondSchema);
        assertThat(firstRelationshipIds).hasSize(10);
        assertThat(firstRelationshipIds).isEqualTo(secondRelationshipIds);
        assertThat(firstRelationshipIds.values()).allMatch(id -> id.version() == 7);
        assertThat(manifestIdentitySnapshot(firstSchema))
                .hasSizeGreaterThan(300)
                .isEqualTo(manifestIdentitySnapshot(secondSchema));

        List<Map<String, Object>> beforeRerun = canonicalAuditSnapshot(firstSchema);
        Map<String, String> completeFixtureBeforeRerun = fixtureTableDigests(firstSchema);
        migrate(firstSchema, configuration, true).migrate();

        assertThat(canonicalAccountIds(firstSchema)).isEqualTo(firstAccountIds);
        assertThat(canonicalRoleAssignmentIds(firstSchema)).isEqualTo(firstRelationshipIds);
        assertThat(canonicalAuditSnapshot(firstSchema)).isEqualTo(beforeRerun);
        assertThat(fixtureTableDigests(firstSchema)).isEqualTo(completeFixtureBeforeRerun);
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-004 and REQ-DEV-FIXTURE-005 - reconciliation -> restore owned state only")
    void reconciliationShouldRestoreOwnedStateAndPreserveUnrelatedRowsAndActivity() {
        String schema = uniqueSchema("dev_fixture_reconciliation");
        Map<String, String> configuration = validConfiguration();
        migrate(schema, configuration, true).migrate();

        assertThat(canonicalAccountCount(schema))
                .as("canonical personas must exist before reconciliation behavior is exercised")
                .isEqualTo(CANONICAL_PERSONA_EMAILS.size());
        UUID sudoId = requiredAccountId(schema, "dev.sudo@example.com");
        UUID sacrificialAccountId = requiredAccountId(schema, "rafael.coord.sandbox@example.com");
        UUID sacrificialMemberId = jdbcTemplate.queryForObject(
                "SELECT id FROM " + schema + ".members WHERE account_id = ?",
                UUID.class,
                sacrificialAccountId
        );
        UUID coordinatorAssignmentId = jdbcTemplate.queryForObject(
                "SELECT ar.id FROM " + schema + ".account_roles ar "
                        + "JOIN " + schema + ".roles r ON r.id = ar.role_id "
                        + "WHERE ar.account_id = ? AND r.name = 'COORD' AND ar.deleted_at IS NULL",
                UUID.class,
                sacrificialAccountId
        );
        String acceptedDisplayName = jdbcTemplate.queryForObject(
                "SELECT display_name FROM " + schema + ".accounts WHERE id = ?",
                String.class,
                sacrificialAccountId
        );

        UUID unrelatedAccountId = UUID.randomUUID();
        String unrelatedEmail = "unrelated." + unrelatedAccountId + "@example.com";
        Timestamp unrelatedCreatedAt = Timestamp.from(Instant.parse("2026-01-02T03:04:05Z"));
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".accounts "
                        + "(id, email, password_hash, display_name, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                unrelatedAccountId,
                unrelatedEmail,
                VALID_PASSWORD_HASH,
                "Unrelated local Account",
                unrelatedCreatedAt,
                unrelatedCreatedAt
        );
        Map<String, Object> unrelatedBefore = jdbcTemplate.queryForMap(
                "SELECT * FROM " + schema + ".accounts WHERE id = ?",
                unrelatedAccountId
        );

        UUID activityId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".activity_logs "
                        + "(id, occurred_at, actor_account_id, actor_kind, action, target_type, "
                        + "target_id, reason, metadata) "
                        + "VALUES (?, ?, ?, 'ACCOUNT', 'MEMBER_DEACTIVATED', 'MEMBER', ?, ?, '{}'::jsonb)",
                activityId,
                Timestamp.from(Instant.parse("2026-02-03T04:05:06Z")),
                sudoId,
                sacrificialMemberId,
                "Manual API workflow evidence"
        );
        long activityCountBefore = activityCount(schema);

        jdbcTemplate.update(
                "UPDATE " + schema + ".accounts SET display_name = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE id = ?",
                "Developer mutation",
                sacrificialAccountId
        );
        jdbcTemplate.update(
                "UPDATE " + schema + ".account_roles SET deleted_at = CURRENT_TIMESTAMP, deleted_by = ? "
                        + "WHERE id = ?",
                sudoId,
                coordinatorAssignmentId
        );

        migrate(schema, configuration, true).migrate();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT display_name FROM " + schema + ".accounts WHERE id = ?",
                String.class,
                sacrificialAccountId
        )).isEqualTo(acceptedDisplayName);
        assertThat(activeRoles(schema, "rafael.coord.sandbox@example.com"))
                .containsExactlyInAnyOrder("COORD", "MEMBER");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT id FROM " + schema + ".account_roles WHERE id = ? AND deleted_at IS NULL",
                UUID.class,
                coordinatorAssignmentId
        )).isEqualTo(coordinatorAssignmentId);
        assertThat(jdbcTemplate.queryForMap(
                "SELECT * FROM " + schema + ".accounts WHERE id = ?",
                unrelatedAccountId
        )).isEqualTo(unrelatedBefore);
        assertThat(activityCount(schema)).isEqualTo(activityCountBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".activity_logs WHERE id = ?",
                Long.class,
                activityId
        )).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-005, REQ-DEV-FIXTURE-006 and REQ-DEV-FIXTURE-009 - workflow states -> relative and synthetic")
    void fixtureShouldProvideRelativeWorkflowStatesAndSyntheticAttachment() throws Exception {
        String schema = uniqueSchema("dev_fixture_workflows");
        migrate(schema, validConfiguration(), true).migrate();

        assertThat(eventStatuses(schema))
                .contains("SCHEDULED", "COMPLETED", "LOCKED", "FINALIZED", "CANCELLED");
        assertThat(oratorioStatuses(schema))
                .contains("SCHEDULED", "COMPLETED", "LOCKED", "FINALIZED", "CANCELLED");
        assertThat(formStatuses(schema))
                .contains("DRAFT", "COMPLETED", "SUPERSEDED", "REVOKED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".events WHERE type = 'MISSA'",
                Long.class
        )).isZero();
        assertThat(activityCount(schema)).isZero();

        LocalDate today = LocalDate.now(SAO_PAULO);
        List<LocalDate> scheduledDates = jdbcTemplate.queryForList(
                "SELECT (begin_date AT TIME ZONE 'America/Sao_Paulo')::date "
                        + "FROM " + schema + ".events "
                        + "WHERE status = 'SCHEDULED' AND deleted_at IS NULL",
                LocalDate.class
        );
        assertThat(scheduledDates)
                .isNotEmpty()
                .allMatch(date -> !date.isBefore(today.plusDays(7)));
        List<LocalDate> closedDates = jdbcTemplate.queryForList(
                "SELECT (end_date AT TIME ZONE 'America/Sao_Paulo')::date "
                        + "FROM " + schema + ".events "
                        + "WHERE status <> 'SCHEDULED' AND deleted_at IS NULL",
                LocalDate.class
        );
        assertThat(closedDates)
                .isNotEmpty()
                .allMatch(date -> !date.isAfter(today.minusDays(7)));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".oratorios "
                        + "WHERE local_date = ? AND deleted_at IS NULL",
                Long.class,
                today.plusDays(60)
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".oratorios o "
                        + "WHERE o.local_date <= ? AND ("
                        + "EXISTS (SELECT 1 FROM " + schema + ".presences p "
                        + "WHERE p.event_id = o.event_id AND p.deleted_at IS NULL) "
                        + "OR EXISTS (SELECT 1 FROM " + schema + ".oratoriano_attendances oa "
                        + "WHERE oa.oratorio_id = o.id AND oa.deleted_at IS NULL))",
                Long.class,
                today.minusDays(370)
        )).isGreaterThanOrEqualTo(1);

        List<Map<String, Object>> attachments = jdbcTemplate.queryForList(
                "SELECT verified_mime_type, byte_length, page_count, sha256, bytes "
                        + "FROM " + schema + ".oratoriano_form_attachments "
                        + "WHERE deleted_at IS NULL ORDER BY id"
        );
        assertThat(attachments).isNotEmpty();
        Map<String, Object> attachment = attachments.getFirst();
        byte[] bytes = (byte[]) attachment.get("bytes");
        assertThat(attachment.get("verified_mime_type")).isEqualTo("application/pdf");
        assertThat(((Number) attachment.get("byte_length")).longValue()).isEqualTo(bytes.length);
        assertThat(attachment.get("sha256")).isEqualTo(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
        );
        assertThat(new String(bytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        try (PDDocument document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages())
                    .isEqualTo(((Number) attachment.get("page_count")).intValue());
            String extractedText = Normalizer.normalize(
                            new PDFTextStripper().getText(document),
                            Normalizer.Form.NFD
                    )
                    .replaceAll("\\p{M}", "")
                    .toLowerCase(Locale.ROOT);
            assertThat(extractedText).containsAnyOf("synthetic", "sintetico");
        }
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-003 and REQ-DEV-FIXTURE-004 - late manifest collision -> atomic rollback")
    void lateManifestCollisionShouldRollbackAllFixtureMutation() {
        String schema = uniqueSchema("dev_fixture_late_collision");
        migrate(schema, Map.of(), false).migrate();
        UUID systemLocationId = UUID.fromString("01950000-0010-7000-8000-000000000001");
        Timestamp begin = Timestamp.from(Instant.parse("2031-04-05T17:00:00Z"));
        Timestamp end = Timestamp.from(Instant.parse("2031-04-05T20:00:00Z"));
        Timestamp now = Timestamp.from(Instant.parse("2026-07-28T12:00:00Z"));
        UUID collidingEventId = UUID.fromString("01950000-0006-7000-8000-000000000001");
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".events "
                        + "(id, title, description, gam_location_id, required_permission_id, "
                        + "type, status, begin_date, end_date, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, NULL, 'MISSA', 'SCHEDULED', ?, ?, ?, ?)",
                collidingEventId,
                "Developer-owned collision",
                "Must remain outside fixture ownership",
                systemLocationId,
                begin,
                end,
                now,
                now
        );

        assertThatThrownBy(() -> migrate(schema, validConfiguration(), true).migrate())
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("Event manifest collision");

        assertThat(canonicalAccountCount(schema)).isZero();
        assertThat(activeRoleCount(schema, "EVENT_SUPPORT")).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT type::text FROM " + schema + ".events WHERE id = ?",
                String.class,
                collidingEventId
        )).isEqualTo("MISSA");
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-003 and REQ-DEV-FIXTURE-004 - claimed Oratoriano key -> atomic collision")
    void oratorianoCanonicalKeyOwnedByAnotherUuidShouldRollbackAllFixtureMutation() {
        String schema = uniqueSchema("dev_fixture_oratoriano_collision");
        migrate(schema, Map.of(), false).migrate();
        UUID developerOwnedId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.parse("2026-07-28T12:00:00Z"));
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".oratorianos "
                        + "(id, first_name, surname, name_key, created_at, updated_at) "
                        + "VALUES (?, 'Developer', 'Owned', 'alice ferreira', ?, ?)",
                developerOwnedId,
                now,
                now
        );

        assertThatThrownBy(() -> migrate(schema, validConfiguration(), true).migrate())
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("Oratoriano manifest collision");

        assertThat(canonicalAccountCount(schema)).isZero();
        assertThat(jdbcTemplate.queryForMap(
                "SELECT first_name, surname, name_key, deleted_at FROM "
                        + schema + ".oratorianos WHERE id = ?",
                developerOwnedId
        )).containsEntry("first_name", "Developer")
                .containsEntry("surname", "Owned")
                .containsEntry("name_key", "alice ferreira")
                .containsEntry("deleted_at", null);
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-004 - missing production-safe prerequisite -> fail before fixture mutation")
    void missingProductionSafePrerequisiteShouldFailBeforeFixtureMutation() {
        String schema = uniqueSchema("dev_fixture_missing_reference");
        migrate(schema, Map.of(), false).migrate();
        jdbcTemplate.update(
                "DELETE FROM " + schema + ".gam_locations WHERE id = ?",
                UUID.fromString("01950000-0010-7000-8000-000000000001")
        );

        assertThatThrownBy(() -> migrate(schema, validConfiguration(), true).migrate())
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("production-safe Oratorio GamLocation");
        assertThat(canonicalAccountCount(schema)).isZero();
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-003 and REQ-DEV-FIXTURE-004 - unrelated relationships -> preserved")
    void unrelatedRelationshipsReferencingCanonicalRecordsShouldSurviveReconciliation() {
        String schema = uniqueSchema("dev_fixture_unrelated_relationships");
        Map<String, String> configuration = validConfiguration();
        migrate(schema, configuration, true).migrate();
        UUID sudoId = requiredAccountId(schema, "dev.sudo@example.com");
        UUID localRoleId = UUID.randomUUID();
        UUID localRoleAssignmentId = UUID.randomUUID();
        UUID localPresenceId = UUID.randomUUID();
        UUID activeMemberId = UUID.fromString("01950000-0004-7000-8000-000000000004");
        UUID scheduledEventId = UUID.fromString("01950000-0006-7000-8000-000000000001");
        UUID localTeamMemberId = UUID.fromString("01950000-0004-7100-8000-000000000010");
        UUID localTeamOratorioId = UUID.fromString("01950000-0006-7100-8000-000000000002");
        Timestamp now = Timestamp.from(Instant.parse("2026-07-28T12:00:00Z"));

        jdbcTemplate.update(
                "INSERT INTO " + schema + ".roles "
                        + "(id, name, description, system_managed, created_at, updated_at) "
                        + "VALUES (?, 'LOCAL_EXPERIMENT', 'Developer-created local Role', FALSE, ?, ?)",
                localRoleId,
                now,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".account_roles "
                        + "(id, account_id, role_id, created_at) VALUES (?, ?, ?, ?)",
                localRoleAssignmentId,
                sudoId,
                localRoleId,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".presences "
                        + "(id, member_id, event_id, observations, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'Developer-created relationship', ?, ?)",
                localPresenceId,
                activeMemberId,
                scheduledEventId,
                now,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".oratorio_team_assignments "
                        + "(oratorio_id, member_id, team_type, created_at) "
                        + "VALUES (?, ?, 'LANCHE', ?)",
                localTeamOratorioId,
                localTeamMemberId,
                now
        );

        migrate(schema, configuration, true).migrate();

        assertSoftly(softly -> {
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT deleted_at IS NULL FROM " + schema + ".account_roles WHERE id = ?",
                    Boolean.class,
                    localRoleAssignmentId
            )).as("unrelated custom Role assignment").isTrue();
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT deleted_at IS NULL FROM " + schema + ".presences WHERE id = ?",
                    Boolean.class,
                    localPresenceId
            )).as("unrelated Presence").isTrue();
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".oratorio_team_assignments "
                            + "WHERE oratorio_id = ? AND member_id = ? AND team_type = 'LANCHE'",
                    Long.class,
                    localTeamOratorioId,
                    localTeamMemberId
            )).as("unrelated Oratorio team assignment").isEqualTo(1);
        });
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-004 and REQ-DEV-FIXTURE-011 - consumed projections -> restored by reconciliation")
    void consumedSacrificialProjectionsShouldBeRestoredAcrossManifestGroups() {
        String schema = uniqueSchema("dev_fixture_consumed_projections");
        Map<String, String> configuration = validConfiguration();
        migrate(schema, configuration, true).migrate();
        Timestamp mutationTime = Timestamp.from(Instant.parse("2026-07-28T12:00:00Z"));

        jdbcTemplate.update(
                "UPDATE " + schema + ".gam_locations "
                        + "SET name = 'Mutated location', identity_name = 'mutated location', "
                        + "deleted_at = ? WHERE id = '01950000-0005-7000-8000-000000000003'",
                mutationTime
        );
        jdbcTemplate.update(
                "UPDATE " + schema + ".events SET title = 'Mutated event', status = 'LOCKED' "
                        + "WHERE id = '01950000-0006-7000-8000-000000000006'"
        );
        jdbcTemplate.update(
                "UPDATE " + schema + ".oratorios SET lanche_description = NULL "
                        + "WHERE id = '01950000-0006-7100-8000-000000000001'"
        );
        jdbcTemplate.update(
                "DELETE FROM " + schema + ".oratorio_team_assignments "
                        + "WHERE oratorio_id = '01950000-0006-7100-8000-000000000001' "
                        + "AND team_type = 'LANCHE'"
        );
        jdbcTemplate.update(
                "UPDATE " + schema + ".membership_solicitations SET first_name = 'Mutated' "
                        + "WHERE id = '01950000-0009-7000-8000-000000000001'"
        );
        jdbcTemplate.update(
                "UPDATE " + schema + ".presences SET deleted_at = ? "
                        + "WHERE id = '01950000-000a-7000-8000-000000000001'",
                mutationTime
        );
        jdbcTemplate.update(
                "UPDATE " + schema + ".oratorianos SET first_name = 'Mutated', "
                        + "name_key = 'mutated alice' "
                        + "WHERE id = '01950000-0008-7000-8000-000000000001'"
        );
        jdbcTemplate.update(
                "UPDATE " + schema + ".oratoriano_attendances SET deleted_at = ? "
                        + "WHERE id = '01950000-000b-7000-8000-000000000001'",
                mutationTime
        );
        jdbcTemplate.update(
                "UPDATE " + schema + ".oratoriano_additional_forms "
                        + "SET draft_revision = 99, draft_data = '{}'::jsonb "
                        + "WHERE id = '01950000-000c-7000-8000-000000000004'"
        );
        jdbcTemplate.update(
                "UPDATE " + schema + ".oratoriano_form_print_snapshots SET deleted_at = ? "
                        + "WHERE id = '01950000-000f-7000-8000-000000000001'",
                mutationTime
        );
        jdbcTemplate.update(
                "UPDATE " + schema + ".oratoriano_form_attachments "
                        + "SET original_filename = 'mutated.pdf' "
                        + "WHERE id = '01950000-000d-7000-8000-000000000001'"
        );

        migrate(schema, configuration, true).migrate();

        assertSoftly(softly -> {
            softly.assertThat(jdbcTemplate.queryForMap(
                    "SELECT name, identity_name, deleted_at FROM " + schema + ".gam_locations "
                            + "WHERE id = '01950000-0005-7000-8000-000000000003'"
            )).containsEntry("name", "Casa de Encontros Esperan\u00e7a")
                    .containsEntry("identity_name", "casa de encontros esperanca")
                    .containsEntry("deleted_at", null);
            softly.assertThat(jdbcTemplate.queryForMap(
                    "SELECT title, status::text AS status FROM " + schema + ".events "
                            + "WHERE id = '01950000-0006-7000-8000-000000000006'"
            )).containsEntry("title", "Evento conclu\u00eddo para bloqueio")
                    .containsEntry("status", "COMPLETED");
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT lanche_description FROM " + schema + ".oratorios "
                            + "WHERE id = '01950000-0006-7100-8000-000000000001'",
                    String.class
            )).isEqualTo("Frutas e suco.");
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".oratorio_team_assignments "
                            + "WHERE oratorio_id = '01950000-0006-7100-8000-000000000001' "
                            + "AND team_type = 'LANCHE'",
                    Long.class
            )).isEqualTo(1);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT first_name FROM " + schema + ".membership_solicitations "
                            + "WHERE id = '01950000-0009-7000-8000-000000000001'",
                    String.class
            )).isEqualTo("Jo\u00e3o");
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT deleted_at IS NULL FROM " + schema + ".presences "
                            + "WHERE id = '01950000-000a-7000-8000-000000000001'",
                    Boolean.class
            )).isTrue();
            softly.assertThat(jdbcTemplate.queryForMap(
                    "SELECT first_name, name_key FROM " + schema + ".oratorianos "
                            + "WHERE id = '01950000-0008-7000-8000-000000000001'"
            )).containsEntry("first_name", "Alice")
                    .containsEntry("name_key", "alice ferreira");
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT deleted_at IS NULL FROM " + schema + ".oratoriano_attendances "
                            + "WHERE id = '01950000-000b-7000-8000-000000000001'",
                    Boolean.class
            )).isTrue();
            softly.assertThat(jdbcTemplate.queryForMap(
                    "SELECT draft_revision, draft_data FROM " + schema + ".oratoriano_additional_forms "
                            + "WHERE id = '01950000-000c-7000-8000-000000000004'"
            )).containsEntry("draft_revision", 3L);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT deleted_at IS NULL FROM " + schema + ".oratoriano_form_print_snapshots "
                            + "WHERE id = '01950000-000f-7000-8000-000000000001'",
                    Boolean.class
            )).isTrue();
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT original_filename FROM " + schema + ".oratoriano_form_attachments "
                            + "WHERE id = '01950000-000d-7000-8000-000000000001'",
                    String.class
            )).isEqualTo("formulario-sintetico-desenvolvimento.pdf");
        });
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-003/004/007 - direct-registration projection -> zero-state restored without touching unrelated Member")
    void directMemberRegistrationShouldRestoreDesignatedAccountZeroStateOnly() {
        String schema = uniqueSchema("dev_fixture_direct_registration");
        Map<String, String> configuration = validConfiguration();
        migrate(schema, configuration, true).migrate();
        Timestamp endpointTime = Timestamp.from(Instant.parse("2026-07-28T12:00:00Z"));
        UUID registrationAccountId =
                requiredAccountId(schema, "beatriz.registration@example.com");
        UUID endpointMemberId =
                UUID.fromString("01970000-1000-7000-8000-000000000001");
        UUID endpointRoleAssignmentId =
                UUID.fromString("01970000-1000-7000-8000-000000000002");
        UUID memberRoleId = jdbcTemplate.queryForObject(
                "SELECT id FROM " + schema + ".roles WHERE name = 'MEMBER'",
                UUID.class
        );

        jdbcTemplate.update(
                "INSERT INTO " + schema + ".members "
                        + "(id, account_id, first_name, surname, birth_date, phone_number, status, "
                        + "created_at, created_by, updated_at, updated_by) "
                        + "VALUES (?, ?, 'Beatriz', 'Campos', DATE '1997-04-10', "
                        + "'+5519998333001', 'ACTIVE', ?, ?, ?, ?)",
                endpointMemberId,
                registrationAccountId,
                endpointTime,
                registrationAccountId,
                endpointTime,
                registrationAccountId
        );
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".account_roles "
                        + "(id, account_id, role_id, created_at, created_by) "
                        + "VALUES (?, ?, ?, ?, ?)",
                endpointRoleAssignmentId,
                registrationAccountId,
                memberRoleId,
                endpointTime,
                registrationAccountId
        );

        UUID unrelatedAccountId =
                UUID.fromString("01970000-1000-7000-8000-000000000003");
        UUID unrelatedMemberId =
                UUID.fromString("01970000-1000-7000-8000-000000000004");
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".accounts "
                        + "(id, email, password_hash, display_name, created_at, updated_at) "
                        + "VALUES (?, 'developer.member@example.com', ?, "
                        + "'Developer-created Account', ?, ?)",
                unrelatedAccountId,
                VALID_PASSWORD_HASH,
                endpointTime,
                endpointTime
        );
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".members "
                        + "(id, account_id, first_name, surname, birth_date, phone_number, status, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, 'Daniela', 'Local', DATE '1993-06-22', "
                        + "'+5519998333002', 'ACTIVE', ?, ?)",
                unrelatedMemberId,
                unrelatedAccountId,
                endpointTime,
                endpointTime
        );
        Map<String, Object> unrelatedMemberBefore = jdbcTemplate.queryForMap(
                "SELECT id, account_id, first_name, surname, birth_date, phone_number, "
                        + "status::text AS status, created_at, updated_at, deleted_at "
                        + "FROM " + schema + ".members WHERE id = ?",
                unrelatedMemberId
        );

        migrate(schema, configuration, true).migrate();

        assertSoftly(softly -> {
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".members WHERE account_id = ?",
                    Long.class,
                    registrationAccountId
            )).as("direct-registration Account restored to no lifetime Member")
                    .isZero();
            softly.assertThat(activeRoles(schema, "beatriz.registration@example.com"))
                    .as("direct-registration Account restored to no lifecycle Role")
                    .isEmpty();
            softly.assertThat(jdbcTemplate.queryForMap(
                    "SELECT id, account_id, first_name, surname, birth_date, phone_number, "
                            + "status::text AS status, created_at, updated_at, deleted_at "
                            + "FROM " + schema + ".members WHERE id = ?",
                    unrelatedMemberId
            )).as("unrelated Developer-created Member")
                    .isEqualTo(unrelatedMemberBefore);
        });
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-003/004/007/009 - solicitation submit and approve -> designated zero states restored only")
    void solicitationSubmitAndApprovalShouldRestoreDesignatedZeroStatesOnly() {
        String schema = uniqueSchema("dev_fixture_solicitation_projection");
        Map<String, String> configuration = validConfiguration();
        migrate(schema, configuration, true).migrate();
        Timestamp endpointTime = Timestamp.from(Instant.parse("2026-07-28T12:00:00Z"));
        UUID selfSubmissionAccountId =
                requiredAccountId(schema, "fernanda.solicitation@example.com");
        UUID approvalAccountId =
                requiredAccountId(schema, "joao.approval@example.com");
        UUID coordinatorAccountId =
                requiredAccountId(schema, "mariana.coord@example.com");
        UUID submittedSolicitationId =
                UUID.fromString("01970000-2000-7000-8000-000000000001");
        UUID approvedMemberId =
                UUID.fromString("01970000-2000-7000-8000-000000000002");
        UUID approvedRoleAssignmentId =
                UUID.fromString("01970000-2000-7000-8000-000000000003");
        UUID memberRoleId = jdbcTemplate.queryForObject(
                "SELECT id FROM " + schema + ".roles WHERE name = 'MEMBER'",
                UUID.class
        );

        jdbcTemplate.update(
                "INSERT INTO " + schema + ".membership_solicitations "
                        + "(id, account_id, first_name, surname, birth_date, phone_number, "
                        + "justification, status, version, created_at, created_by, updated_at, updated_by) "
                        + "VALUES (?, ?, 'Fernanda', 'Lima', DATE '1995-07-18', "
                        + "'+5519998444001', 'Endpoint-equivalent self submission', "
                        + "'PENDING', 0, ?, ?, ?, ?)",
                submittedSolicitationId,
                selfSubmissionAccountId,
                endpointTime,
                selfSubmissionAccountId,
                endpointTime,
                selfSubmissionAccountId
        );
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".members "
                        + "(id, account_id, first_name, surname, birth_date, phone_number, status, "
                        + "created_at, created_by, updated_at, updated_by) "
                        + "VALUES (?, ?, 'João', 'Pereira', DATE '1999-03-15', "
                        + "'+5519998222001', 'ACTIVE', ?, ?, ?, ?)",
                approvedMemberId,
                approvalAccountId,
                endpointTime,
                coordinatorAccountId,
                endpointTime,
                coordinatorAccountId
        );
        jdbcTemplate.update(
                "UPDATE " + schema + ".membership_solicitations "
                        + "SET status = 'APPROVED', reviewed_by_account_id = ?, decided_at = ?, "
                        + "review_reason = 'Endpoint-equivalent approval', member_id = ?, "
                        + "version = version + 1, updated_at = ?, updated_by = ? "
                        + "WHERE id = '01950000-0009-7000-8000-000000000001'",
                coordinatorAccountId,
                endpointTime,
                approvedMemberId,
                endpointTime,
                coordinatorAccountId
        );
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".account_roles "
                        + "(id, account_id, role_id, created_at, created_by) "
                        + "VALUES (?, ?, ?, ?, ?)",
                approvedRoleAssignmentId,
                approvalAccountId,
                memberRoleId,
                endpointTime,
                coordinatorAccountId
        );

        UUID unrelatedAccountId =
                UUID.fromString("01970000-2000-7000-8000-000000000004");
        UUID unrelatedSolicitationId =
                UUID.fromString("01970000-2000-7000-8000-000000000005");
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".accounts "
                        + "(id, email, password_hash, display_name, created_at, updated_at) "
                        + "VALUES (?, 'developer.solicitation@example.com', ?, "
                        + "'Developer-created solicitation Account', ?, ?)",
                unrelatedAccountId,
                VALID_PASSWORD_HASH,
                endpointTime,
                endpointTime
        );
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".membership_solicitations "
                        + "(id, account_id, first_name, surname, birth_date, phone_number, "
                        + "justification, status, version, created_at, updated_at) "
                        + "VALUES (?, ?, 'Sofia', 'Local', DATE '1992-09-11', "
                        + "'+5519998444002', 'Developer-created pending solicitation', "
                        + "'PENDING', 0, ?, ?)",
                unrelatedSolicitationId,
                unrelatedAccountId,
                endpointTime,
                endpointTime
        );
        Map<String, Object> unrelatedSolicitationBefore = jdbcTemplate.queryForMap(
                "SELECT id, account_id, first_name, surname, birth_date, phone_number, "
                        + "justification, status::text AS status, version, created_at, updated_at, "
                        + "deleted_at FROM " + schema + ".membership_solicitations WHERE id = ?",
                unrelatedSolicitationId
        );

        migrate(schema, configuration, true).migrate();

        assertSoftly(softly -> {
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".membership_solicitations "
                            + "WHERE account_id = ? AND deleted_at IS NULL",
                    Long.class,
                    selfSubmissionAccountId
            )).as("self-submission Account restored to no active solicitation")
                    .isZero();
            softly.assertThat(jdbcTemplate.queryForMap(
                    "SELECT status::text AS status, member_id FROM "
                            + schema + ".membership_solicitations "
                            + "WHERE id = '01950000-0009-7000-8000-000000000001'"
            )).as("approval target restored to its canonical pending solicitation")
                    .containsEntry("status", "PENDING")
                    .containsEntry("member_id", null);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".members WHERE account_id = ?",
                    Long.class,
                    approvalAccountId
            )).as("approval target restored to no lifetime Member")
                    .isZero();
            softly.assertThat(activeRoles(schema, "joao.approval@example.com"))
                    .as("approval target restored to no lifecycle Role")
                    .isEmpty();
            softly.assertThat(jdbcTemplate.queryForMap(
                    "SELECT id, account_id, first_name, surname, birth_date, phone_number, "
                            + "justification, status::text AS status, version, created_at, updated_at, "
                            + "deleted_at FROM " + schema + ".membership_solicitations WHERE id = ?",
                    unrelatedSolicitationId
            )).as("unrelated Developer-created solicitation")
                    .isEqualTo(unrelatedSolicitationBefore);
        });
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-003/004/009 and REQ-ORATORIANO-FORM-013/019 - attachment replacement -> canonical collection restored only")
    void signedAttachmentReplacementShouldRestoreDesignatedCollectionOnly() {
        String schema = uniqueSchema("dev_fixture_attachment_projection");
        Map<String, String> configuration = validConfiguration();
        migrate(schema, configuration, true).migrate();
        Timestamp endpointTime = Timestamp.from(Instant.parse("2026-07-28T12:00:00Z"));
        UUID actorId = requiredAccountId(schema, "mariana.coord@example.com");
        UUID canonicalAttachmentId =
                UUID.fromString("01950000-000d-7000-8000-000000000001");
        UUID replacementAttachmentId =
                UUID.fromString("01970000-3000-7000-8000-000000000001");

        jdbcTemplate.update(
                "UPDATE " + schema + ".oratoriano_form_attachments "
                        + "SET deleted_at = ?, deleted_by = ? WHERE id = ?",
                endpointTime,
                actorId,
                canonicalAttachmentId
        );
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".oratoriano_form_attachments "
                        + "(id, form_id, original_filename, verified_mime_type, byte_length, "
                        + "page_order, page_count, sha256, bytes, created_at, created_by, "
                        + "updated_at, updated_by) "
                        + "SELECT ?, form_id, 'endpoint-replacement.pdf', verified_mime_type, "
                        + "byte_length, page_order, page_count, sha256, bytes, ?, ?, ?, ? "
                        + "FROM " + schema + ".oratoriano_form_attachments WHERE id = ?",
                replacementAttachmentId,
                endpointTime,
                actorId,
                endpointTime,
                actorId,
                canonicalAttachmentId
        );

        UUID developerFormId =
                UUID.fromString("01970000-3000-7000-8000-000000000002");
        UUID developerAttachmentId =
                UUID.fromString("01970000-3000-7000-8000-000000000003");
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".oratoriano_additional_forms "
                        + "(id, oratoriano_id, version, status, origin, draft_revision, draft_data, "
                        + "created_at, created_by, updated_at, updated_by) "
                        + "VALUES (?, '01950000-0008-7000-8000-000000000007', 2, 'DRAFT', "
                        + "'DIRECT_SYSTEM_ENTRY', 1, '{}'::jsonb, ?, ?, ?, ?)",
                developerFormId,
                endpointTime,
                actorId,
                endpointTime,
                actorId
        );
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".oratoriano_form_attachments "
                        + "(id, form_id, original_filename, verified_mime_type, byte_length, "
                        + "page_order, page_count, sha256, bytes, created_at, created_by, "
                        + "updated_at, updated_by) "
                        + "SELECT ?, ?, 'developer-created.pdf', verified_mime_type, "
                        + "byte_length, page_order, page_count, sha256, bytes, ?, ?, ?, ? "
                        + "FROM " + schema + ".oratoriano_form_attachments WHERE id = ?",
                developerAttachmentId,
                developerFormId,
                endpointTime,
                actorId,
                endpointTime,
                actorId,
                canonicalAttachmentId
        );
        Map<String, Object> developerAttachmentBefore = jdbcTemplate.queryForMap(
                "SELECT id, form_id, original_filename, verified_mime_type, byte_length, "
                        + "page_order, page_count, sha256, created_at, created_by, updated_at, "
                        + "updated_by, deleted_at FROM " + schema
                        + ".oratoriano_form_attachments WHERE id = ?",
                developerAttachmentId
        );

        migrate(schema, configuration, true).migrate();

        assertSoftly(softly -> {
            softly.assertThat(jdbcTemplate.queryForList(
                    "SELECT id FROM " + schema + ".oratoriano_form_attachments "
                            + "WHERE form_id = '01950000-000c-7000-8000-000000000006' "
                            + "AND deleted_at IS NULL",
                    UUID.class
            )).as("completion-ready draft canonical attachment collection")
                    .containsExactly(canonicalAttachmentId);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".oratoriano_form_attachments "
                            + "WHERE id = ? AND deleted_at IS NULL",
                    Long.class,
                    replacementAttachmentId
            )).as("endpoint-created replacement no longer active")
                    .isZero();
            softly.assertThat(jdbcTemplate.queryForMap(
                    "SELECT id, form_id, original_filename, verified_mime_type, byte_length, "
                            + "page_order, page_count, sha256, created_at, created_by, updated_at, "
                            + "updated_by, deleted_at FROM " + schema
                            + ".oratoriano_form_attachments WHERE id = ?",
                    developerAttachmentId
            )).as("unrelated Developer-created form attachment")
                    .isEqualTo(developerAttachmentBefore);
        });
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-003/004/009 and REQ-ORATORIANO-FORM-011/013 - newer print -> canonical latest projection restored only")
    void newerEndpointPrintSnapshotShouldRestoreCanonicalLatestProjectionOnly() {
        String schema = uniqueSchema("dev_fixture_print_projection");
        Map<String, String> configuration = validConfiguration();
        migrate(schema, configuration, true).migrate();
        Timestamp endpointTime = Timestamp.from(Instant.parse("2026-07-28T12:00:00Z"));
        UUID actorId = requiredAccountId(schema, "mariana.coord@example.com");
        UUID canonicalFormId =
                UUID.fromString("01950000-000c-7000-8000-000000000006");
        UUID canonicalSnapshotId =
                UUID.fromString("01950000-000f-7000-8000-000000000001");
        UUID canonicalAttachmentId =
                UUID.fromString("01950000-000d-7000-8000-000000000001");
        UUID endpointSnapshotId =
                UUID.fromString("01970000-4000-7000-8000-000000000001");

        jdbcTemplate.update(
                "INSERT INTO " + schema + ".oratoriano_form_print_snapshots "
                        + "(id, form_id, draft_revision, mode, generated_at, template_version, "
                        + "page_count, captured_data, fingerprint, created_at, created_by, "
                        + "updated_at, updated_by) "
                        + "SELECT ?, form_id, draft_revision, mode, generated_at + INTERVAL '1 day', "
                        + "template_version, page_count, captured_data, fingerprint, ?, ?, ?, ? "
                        + "FROM " + schema + ".oratoriano_form_print_snapshots WHERE id = ?",
                endpointSnapshotId,
                endpointTime,
                actorId,
                endpointTime,
                actorId,
                canonicalSnapshotId
        );

        UUID developerFormId =
                UUID.fromString("01970000-4000-7000-8000-000000000002");
        UUID developerSnapshotId =
                UUID.fromString("01970000-4000-7000-8000-000000000003");
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".oratoriano_additional_forms "
                        + "(id, oratoriano_id, version, status, origin, draft_revision, draft_data, "
                        + "created_at, created_by, updated_at, updated_by) "
                        + "VALUES (?, '01950000-0008-7000-8000-000000000007', 2, 'DRAFT', "
                        + "'DIRECT_SYSTEM_ENTRY', 1, '{}'::jsonb, ?, ?, ?, ?)",
                developerFormId,
                endpointTime,
                actorId,
                endpointTime,
                actorId
        );
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".oratoriano_form_print_snapshots "
                        + "(id, form_id, draft_revision, mode, generated_at, template_version, "
                        + "page_count, captured_data, fingerprint, created_at, created_by, "
                        + "updated_at, updated_by) "
                        + "SELECT ?, ?, 1, 'PREFILLED', generated_at + INTERVAL '2 days', "
                        + "template_version, page_count, '{}'::jsonb, fingerprint, ?, ?, ?, ? "
                        + "FROM " + schema + ".oratoriano_form_print_snapshots WHERE id = ?",
                developerSnapshotId,
                developerFormId,
                endpointTime,
                actorId,
                endpointTime,
                actorId,
                canonicalSnapshotId
        );
        Map<String, Object> developerSnapshotBefore = jdbcTemplate.queryForMap(
                "SELECT id, form_id, draft_revision, mode::text AS mode, generated_at, "
                        + "template_version, page_count, captured_data, fingerprint, "
                        + "created_at, created_by, updated_at, updated_by, deleted_at "
                        + "FROM " + schema + ".oratoriano_form_print_snapshots WHERE id = ?",
                developerSnapshotId
        );

        migrate(schema, configuration, true).migrate();

        assertSoftly(softly -> {
            softly.assertThat(jdbcTemplate.queryForList(
                    "SELECT id FROM " + schema + ".oratoriano_form_print_snapshots "
                            + "WHERE form_id = ? AND deleted_at IS NULL "
                            + "ORDER BY generated_at DESC, id DESC",
                    UUID.class,
                    canonicalFormId
            )).as("completion-ready draft exact active print-snapshot projection")
                    .containsExactly(canonicalSnapshotId);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".oratoriano_form_print_snapshots "
                            + "WHERE id = ? AND deleted_at IS NULL",
                    Long.class,
                    endpointSnapshotId
            )).as("newer endpoint-created print snapshot no longer active")
                    .isZero();
            softly.assertThat(jdbcTemplate.queryForMap(
                    "SELECT snapshot.id, snapshot.mode::text AS mode, "
                            + "snapshot.draft_revision, snapshot.captured_data, "
                            + "form_record.draft_revision AS form_draft_revision, "
                            + "form_record.draft_data AS form_draft_data "
                            + "FROM " + schema + ".oratoriano_form_print_snapshots snapshot "
                            + "JOIN " + schema + ".oratoriano_additional_forms form_record "
                            + "ON form_record.id = snapshot.form_id "
                            + "WHERE snapshot.form_id = ? AND snapshot.deleted_at IS NULL "
                            + "ORDER BY snapshot.generated_at DESC, snapshot.id DESC LIMIT 1",
                    canonicalFormId
            )).as("latest completion-ready canonical snapshot")
                    .containsEntry("id", canonicalSnapshotId)
                    .containsEntry("mode", "PREFILLED")
                    .satisfies(snapshot -> {
                        softly.assertThat(snapshot.get("draft_revision"))
                                .isEqualTo(snapshot.get("form_draft_revision"));
                        softly.assertThat(snapshot.get("captured_data"))
                                .isEqualTo(snapshot.get("form_draft_data"));
                    });
            softly.assertThat(jdbcTemplate.queryForList(
                    "SELECT id FROM " + schema + ".oratoriano_form_attachments "
                            + "WHERE form_id = ? AND deleted_at IS NULL "
                            + "ORDER BY page_order, id",
                    UUID.class,
                    canonicalFormId
            )).as("completion-ready draft exact active attachment projection")
                    .containsExactly(canonicalAttachmentId);
            softly.assertThat(jdbcTemplate.queryForMap(
                    "SELECT id, form_id, draft_revision, mode::text AS mode, generated_at, "
                            + "template_version, page_count, captured_data, fingerprint, "
                            + "created_at, created_by, updated_at, updated_by, deleted_at "
                            + "FROM " + schema + ".oratoriano_form_print_snapshots WHERE id = ?",
                    developerSnapshotId
            )).as("unrelated Developer-created print snapshot")
                    .isEqualTo(developerSnapshotBefore);
        });
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-006/009 and REQ-ORATORIANO-FORM-002/010/012/013 - immutable forms -> corresponding completion history")
    void immutableFormFixturesShouldRetainCompletionValidDataAndArtifacts() throws Exception {
        String schema = uniqueSchema("dev_fixture_immutable_forms");
        migrate(schema, validConfiguration(), true).migrate();

        long immutableFormCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".oratoriano_additional_forms "
                        + "WHERE status IN ('COMPLETED', 'SUPERSEDED', 'REVOKED') "
                        + "AND deleted_at IS NULL",
                Long.class
        );
        long completionValidDataCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".oratoriano_additional_forms "
                        + "WHERE status IN ('COMPLETED', 'SUPERSEDED', 'REVOKED') "
                        + "AND deleted_at IS NULL AND signed_on IS NOT NULL "
                        + "AND completed_at IS NOT NULL "
                        + "AND draft_data ?& ARRAY["
                        + "'firstName', 'surname', 'birthDate', 'cpf', 'address', "
                        + "'responsible', 'health', 'declarations', 'signedOn'] "
                        + "AND draft_data #>> '{address,addressLine}' IS NOT NULL "
                        + "AND draft_data #>> '{address,addressNumber}' IS NOT NULL "
                        + "AND draft_data #>> '{address,neighborhood}' IS NOT NULL "
                        + "AND draft_data #>> '{address,cep}' IS NOT NULL "
                        + "AND draft_data #>> '{address,city}' IS NOT NULL "
                        + "AND draft_data #>> '{responsible,relationship}' IS NOT NULL "
                        + "AND draft_data #>> '{responsible,atLeast18}' = 'true' "
                        + "AND draft_data #>> '{health,medicalFollowUp,answer}' "
                        + "IN ('YES', 'NO', 'NOT_INFORMED') "
                        + "AND draft_data #>> '{health,physicalActivityRestriction,answer}' "
                        + "IN ('YES', 'NO', 'NOT_INFORMED') "
                        + "AND draft_data #>> '{health,medicineUse,answer}' "
                        + "IN ('YES', 'NO', 'NOT_INFORMED') "
                        + "AND draft_data #>> '{health,allergies,answer}' "
                        + "IN ('YES', 'NO', 'NOT_INFORMED') "
                        + "AND draft_data #>> '{health,convulsions,answer}' "
                        + "IN ('YES', 'NO', 'NOT_INFORMED') "
                        + "AND draft_data #>> '{health,frequentFainting,answer}' "
                        + "IN ('YES', 'NO', 'NOT_INFORMED') "
                        + "AND draft_data #>> '{health,heartCondition,answer}' "
                        + "IN ('YES', 'NO', 'NOT_INFORMED') "
                        + "AND draft_data #>> '{health,otherHealthCondition,answer}' "
                        + "IN ('YES', 'NO', 'NOT_INFORMED') "
                        + "AND draft_data #>> '{declarations,signerRelationshipConfirmed}' = 'true' "
                        + "AND draft_data #>> '{declarations,informationTruthConfirmed}' = 'true' "
                        + "AND draft_data #>> '{declarations,healthInformationCurrentConfirmed}' = 'true' "
                        + "AND draft_data #>> '{declarations,informationUseUnderstood}' = 'true' "
                        + "AND draft_data #>> '{declarations,formReviewed}' = 'true' "
                        + "AND draft_data #>> '{declarations,imageAndVoiceAuthorizationAccepted}' = 'true'",
                Long.class
        );
        long immutableFormsWithOneArtifactSet = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".oratoriano_additional_forms form_record "
                        + "WHERE form_record.status IN ('COMPLETED', 'SUPERSEDED', 'REVOKED') "
                        + "AND form_record.deleted_at IS NULL "
                        + "AND (SELECT COUNT(*) FROM " + schema + ".oratoriano_form_print_snapshots snapshot "
                        + "WHERE snapshot.form_id = form_record.id AND snapshot.deleted_at IS NULL) = 1 "
                        + "AND (SELECT COUNT(*) FROM " + schema + ".oratoriano_form_attachments attachment "
                        + "WHERE attachment.form_id = form_record.id AND attachment.deleted_at IS NULL) = 1",
                Long.class
        );
        List<Map<String, Object>> immutableArtifacts = jdbcTemplate.queryForList(
                "SELECT form_record.id AS form_id, form_record.origin::text AS origin, "
                        + "form_record.signed_on::text AS signed_on, "
                        + "form_record.draft_data #>> '{signedOn}' AS structured_signed_on, "
                        + "snapshot.id AS snapshot_id, snapshot.mode::text AS snapshot_mode, "
                        + "attachment.id AS attachment_id, attachment.verified_mime_type, "
                        + "attachment.byte_length, attachment.page_count, attachment.sha256, "
                        + "attachment.bytes "
                        + "FROM " + schema + ".oratoriano_additional_forms form_record "
                        + "JOIN " + schema + ".oratoriano_form_print_snapshots snapshot "
                        + "ON snapshot.form_id = form_record.id AND snapshot.deleted_at IS NULL "
                        + "JOIN " + schema + ".oratoriano_form_attachments attachment "
                        + "ON attachment.form_id = form_record.id AND attachment.deleted_at IS NULL "
                        + "WHERE form_record.status IN ('COMPLETED', 'SUPERSEDED', 'REVOKED') "
                        + "AND form_record.deleted_at IS NULL ORDER BY form_record.id"
        );

        assertSoftly(softly -> {
            softly.assertThat(immutableFormCount).isGreaterThanOrEqualTo(3);
            softly.assertThat(completionValidDataCount)
                    .as("immutable forms with completion-valid structured data")
                    .isEqualTo(immutableFormCount);
            softly.assertThat(immutableFormsWithOneArtifactSet)
                    .as("immutable forms with exactly one active snapshot and complete PDF")
                    .isEqualTo(immutableFormCount);
            softly.assertThat(immutableArtifacts)
                    .as("one corresponding artifact set for every immutable form")
                    .hasSize((int) immutableFormCount);
        });

        for (Map<String, Object> artifact : immutableArtifacts) {
            UUID formId = (UUID) artifact.get("form_id");
            UUID snapshotId = (UUID) artifact.get("snapshot_id");
            String origin = (String) artifact.get("origin");
            String signedOn = (String) artifact.get("signed_on");
            String expectedMode = "DIRECT_SYSTEM_ENTRY".equals(origin)
                    ? "PREFILLED"
                    : "IDENTIFIED_BLANK";
            byte[] bytes = (byte[]) artifact.get("bytes");
            String pdfText = normalizedPdfText(bytes);
            String actualSha256 = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)
            );

            assertSoftly(softly -> {
                softly.assertThat(artifact.get("structured_signed_on"))
                        .as("structured signedOn for form %s", formId)
                        .isEqualTo(signedOn);
                softly.assertThat(artifact.get("snapshot_mode"))
                        .as("origin-appropriate snapshot mode for form %s", formId)
                        .isEqualTo(expectedMode);
                softly.assertThat(artifact.get("verified_mime_type"))
                        .as("verified MIME type for form %s", formId)
                        .isEqualTo("application/pdf");
                softly.assertThat(((Number) artifact.get("byte_length")).longValue())
                        .as("byte length for form %s", formId)
                        .isEqualTo(bytes.length);
                softly.assertThat(artifact.get("sha256"))
                        .as("SHA-256 for form %s", formId)
                        .isEqualTo(actualSha256);
                softly.assertThat(pdfText)
                        .as("signed attachment identity for form %s", formId)
                        .contains(formId.toString(), snapshotId.toString(), signedOn);
            });

            try (PDDocument document = Loader.loadPDF(bytes)) {
                assertThat(document.getNumberOfPages())
                        .as("page count for form %s", formId)
                        .isEqualTo(((Number) artifact.get("page_count")).intValue());
            }
        }
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-006/009 and REQ-ORATORIANO-006/FORM-006/017/018 - completed and revoked forms -> profile provenance and SELF snapshot")
    void completedAndRevokedFormsShouldRetainProfileProvenanceAndCanonicalSelfSnapshot() {
        String schema = uniqueSchema("dev_fixture_form_profile_projection");
        migrate(schema, validConfiguration(), true).migrate();

        List<Map<String, Object>> completionProducedProfiles = jdbcTemplate.queryForList(
                "SELECT form_record.id AS form_id, form_record.status::text AS status, "
                        + "form_record.signed_on::text AS signed_on, "
                        + "form_record.draft_data #>> '{firstName}' AS form_first_name, "
                        + "form_record.draft_data #>> '{surname}' AS form_surname, "
                        + "form_record.draft_data #>> '{birthDate}' AS form_birth_date, "
                        + "form_record.draft_data #>> '{phoneNumber}' AS form_phone, "
                        + "form_record.draft_data #>> '{cpf}' AS form_cpf, "
                        + "form_record.draft_data #>> '{responsible,relationship}' "
                        + "AS responsible_relationship, "
                        + "form_record.draft_data #>> '{responsible,firstName}' "
                        + "AS responsible_first_name, "
                        + "form_record.draft_data #>> '{responsible,surname}' "
                        + "AS responsible_surname, "
                        + "form_record.draft_data #>> '{responsible,cpf}' AS responsible_cpf, "
                        + "form_record.draft_data #>> '{responsible,phoneNumber}' "
                        + "AS responsible_phone, "
                        + "person.first_name AS profile_first_name, "
                        + "person.surname AS profile_surname, "
                        + "person.birth_date::text AS profile_birth_date, "
                        + "person.phone_number AS profile_phone, "
                        + "person.name_source_form_id, "
                        + "person.name_source_signed_on::text AS name_source_signed_on, "
                        + "person.birth_date_source_form_id, "
                        + "person.birth_date_source_signed_on::text "
                        + "AS birth_date_source_signed_on, "
                        + "person.phone_source_form_id, "
                        + "person.phone_source_signed_on::text AS phone_source_signed_on "
                        + "FROM " + schema + ".oratoriano_additional_forms form_record "
                        + "JOIN " + schema + ".oratorianos person "
                        + "ON person.id = form_record.oratoriano_id "
                        + "WHERE form_record.status IN ('COMPLETED', 'REVOKED') "
                        + "AND form_record.deleted_at IS NULL ORDER BY form_record.id"
        );

        assertSoftly(softly -> {
            softly.assertThat(completionProducedProfiles)
                    .as("immutable completed and revoked fixture forms")
                    .hasSizeGreaterThanOrEqualTo(2);
            softly.assertThat(completionProducedProfiles)
                    .extracting(profile -> profile.get("status"))
                    .contains("COMPLETED", "REVOKED");

            for (Map<String, Object> profile : completionProducedProfiles) {
                UUID formId = (UUID) profile.get("form_id");
                String signedOn = (String) profile.get("signed_on");

                softly.assertThat(profile.get("profile_first_name"))
                        .as("completion-produced first name for form %s", formId)
                        .isEqualTo(profile.get("form_first_name"));
                softly.assertThat(profile.get("profile_surname"))
                        .as("completion-produced surname for form %s", formId)
                        .isEqualTo(profile.get("form_surname"));
                softly.assertThat(profile.get("profile_birth_date"))
                        .as("completion-produced birth date for form %s", formId)
                        .isEqualTo(profile.get("form_birth_date"));
                softly.assertThat(profile.get("name_source_form_id"))
                        .as("name provenance form for %s", formId)
                        .isEqualTo(formId);
                softly.assertThat(profile.get("name_source_signed_on"))
                        .as("name provenance effective date for %s", formId)
                        .isEqualTo(signedOn);
                softly.assertThat(profile.get("birth_date_source_form_id"))
                        .as("birth-date provenance form for %s", formId)
                        .isEqualTo(formId);
                softly.assertThat(profile.get("birth_date_source_signed_on"))
                        .as("birth-date provenance effective date for %s", formId)
                        .isEqualTo(signedOn);

                if (profile.get("form_phone") != null) {
                    softly.assertThat(profile.get("profile_phone"))
                            .as("completion-produced phone for form %s", formId)
                            .isEqualTo(profile.get("form_phone"));
                    softly.assertThat(profile.get("phone_source_form_id"))
                            .as("phone provenance form for %s", formId)
                            .isEqualTo(formId);
                    softly.assertThat(profile.get("phone_source_signed_on"))
                            .as("phone provenance effective date for %s", formId)
                            .isEqualTo(signedOn);
                }

                if ("SELF".equals(profile.get("responsible_relationship"))) {
                    softly.assertThat(profile.get("responsible_first_name"))
                            .as("SELF responsible first name for form %s", formId)
                            .isEqualTo(profile.get("form_first_name"));
                    softly.assertThat(profile.get("responsible_surname"))
                            .as("SELF responsible surname for form %s", formId)
                            .isEqualTo(profile.get("form_surname"));
                    softly.assertThat(profile.get("responsible_cpf"))
                            .as("SELF responsible CPF for form %s", formId)
                            .isEqualTo(profile.get("form_cpf"));
                    softly.assertThat(profile.get("responsible_phone"))
                            .as("SELF responsible phone for form %s", formId)
                            .isEqualTo(profile.get("form_phone"));
                }
            }
            softly.assertThat(completionProducedProfiles)
                    .as("canonical immutable SELF-responsible forms")
                    .anySatisfy(profile -> assertThat(profile.get("responsible_relationship"))
                            .isEqualTo("SELF"));
        });
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-004/005 and REQ-PERSISTENCE-003 - accepted-deleted rows -> deletion preserves updatedAt")
    void reconcilingRestoredAcceptedDeletedRowsShouldPreserveUpdateAuditTimestamp() {
        String schema = uniqueSchema("dev_fixture_deleted_audit");
        Map<String, String> configuration = validConfiguration();
        migrate(schema, configuration, true).migrate();
        Timestamp restoredUpdate = Timestamp.from(Instant.parse("2026-07-01T12:00:00Z"));

        jdbcTemplate.update(
                "UPDATE " + schema + ".roles SET updated_at = ?, updated_by = NULL, "
                        + "deleted_at = NULL, deleted_by = NULL "
                        + "WHERE name = 'ARCHIVED_EVENT_SUPPORT'",
                restoredUpdate
        );
        jdbcTemplate.update(
                "UPDATE " + schema + ".gam_locations SET updated_at = ?, updated_by = NULL, "
                        + "deleted_at = NULL, deleted_by = NULL "
                        + "WHERE id = '01950000-0005-7000-8000-000000000005'",
                restoredUpdate
        );
        jdbcTemplate.update(
                "UPDATE " + schema + ".oratorianos SET updated_at = ?, updated_by = NULL, "
                        + "deleted_at = NULL, deleted_by = NULL "
                        + "WHERE id = '01950000-0008-7000-8000-000000000099'",
                restoredUpdate
        );
        jdbcTemplate.update(
                "UPDATE " + schema + ".presences SET updated_at = ?, updated_by = NULL, "
                        + "deleted_at = NULL, deleted_by = NULL "
                        + "WHERE id = '01950000-000a-7000-8000-000000000004'",
                restoredUpdate
        );
        jdbcTemplate.update(
                "UPDATE " + schema + ".oratoriano_attendances SET updated_at = ?, updated_by = NULL, "
                        + "deleted_at = NULL, deleted_by = NULL "
                        + "WHERE id = '01950000-000b-7000-8000-000000000004'",
                restoredUpdate
        );

        migrate(schema, configuration, true).migrate();

        assertSoftly(softly -> {
            softly.assertThat(jdbcTemplate.queryForMap(
                    "SELECT updated_at, deleted_at IS NOT NULL AS deleted "
                            + "FROM " + schema + ".roles WHERE name = 'ARCHIVED_EVENT_SUPPORT'"
            )).containsEntry("updated_at", restoredUpdate)
                    .containsEntry("deleted", true);
            softly.assertThat(jdbcTemplate.queryForMap(
                    "SELECT updated_at, deleted_at IS NOT NULL AS deleted "
                            + "FROM " + schema + ".gam_locations "
                            + "WHERE id = '01950000-0005-7000-8000-000000000005'"
            )).containsEntry("updated_at", restoredUpdate)
                    .containsEntry("deleted", true);
            softly.assertThat(jdbcTemplate.queryForMap(
                    "SELECT updated_at, deleted_at IS NOT NULL AS deleted "
                            + "FROM " + schema + ".oratorianos "
                            + "WHERE id = '01950000-0008-7000-8000-000000000099'"
            )).containsEntry("updated_at", restoredUpdate)
                    .containsEntry("deleted", true);
            softly.assertThat(jdbcTemplate.queryForMap(
                    "SELECT updated_at, deleted_at IS NOT NULL AS deleted "
                            + "FROM " + schema + ".presences "
                            + "WHERE id = '01950000-000a-7000-8000-000000000004'"
            )).containsEntry("updated_at", restoredUpdate)
                    .containsEntry("deleted", true);
            softly.assertThat(jdbcTemplate.queryForMap(
                    "SELECT updated_at, deleted_at IS NOT NULL AS deleted "
                            + "FROM " + schema + ".oratoriano_attendances "
                            + "WHERE id = '01950000-000b-7000-8000-000000000004'"
            )).containsEntry("updated_at", restoredUpdate)
                    .containsEntry("deleted", true);
        });
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-004/005 and REQ-PERSISTENCE-003/010 - accepted-active rows -> restoration advances updatedAt")
    void reconcilingSoftDeletedAcceptedActiveRowsShouldRecordRestorationUpdate() {
        String schema = uniqueSchema("dev_fixture_restoration_audit");
        Map<String, String> configuration = validConfiguration();
        migrate(schema, configuration, true).migrate();
        Timestamp priorUpdate = Timestamp.from(Instant.parse("2026-07-01T12:00:00Z"));
        Timestamp deletionTime = Timestamp.from(Instant.parse("2026-07-20T12:00:00Z"));
        UUID actorId = requiredAccountId(schema, "dev.sudo@example.com");
        Map<String, String> acceptedActiveRows = Map.of(
                "gam_locations", "01950000-0005-7000-8000-000000000003",
                "oratorianos", "01950000-0008-7000-8000-000000000001",
                "presences", "01950000-000a-7000-8000-000000000001",
                "oratoriano_attendances", "01950000-000b-7000-8000-000000000001"
        );
        Map<String, Timestamp> creationTimestamps = new LinkedHashMap<>();

        acceptedActiveRows.forEach((table, id) -> {
            creationTimestamps.put(table, jdbcTemplate.queryForObject(
                    "SELECT created_at FROM " + schema + "." + table + " WHERE id = ?::uuid",
                    Timestamp.class,
                    id
            ));
            jdbcTemplate.update(
                    "UPDATE " + schema + "." + table + " "
                            + "SET updated_at = ?, updated_by = ?, deleted_at = ?, deleted_by = ? "
                            + "WHERE id = ?::uuid",
                    priorUpdate,
                    actorId,
                    deletionTime,
                    actorId,
                    id
            );
        });

        migrate(schema, configuration, true).migrate();

        assertSoftly(softly -> acceptedActiveRows.forEach((table, id) -> {
            Map<String, Object> restored = jdbcTemplate.queryForMap(
                    "SELECT created_at, updated_at, updated_by, deleted_at, deleted_by "
                            + "FROM " + schema + "." + table + " WHERE id = ?::uuid",
                    id
            );
            softly.assertThat(restored.get("created_at"))
                    .as("%s creation audit", table)
                    .isEqualTo(creationTimestamps.get(table));
            softly.assertThat((Timestamp) restored.get("updated_at"))
                    .as("%s restoration update timestamp", table)
                    .isAfter(deletionTime);
            softly.assertThat(restored.get("updated_by"))
                    .as("%s system restoration actor", table)
                    .isNull();
            softly.assertThat(restored.get("deleted_at"))
                    .as("%s deletion timestamp cleared", table)
                    .isNull();
            softly.assertThat(restored.get("deleted_by"))
                    .as("%s deletion actor cleared", table)
                    .isNull();
        }));
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-011 - locked lifecycle workflows -> independent Generic Event and Oratorio targets")
    void lockedFinalizeAndReopenWorkflowsShouldHaveIndependentSacrificialTargets() {
        String schema = uniqueSchema("dev_fixture_locked_targets");
        migrate(schema, validConfiguration(), true).migrate();

        assertSoftly(softly -> {
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".events "
                            + "WHERE type = 'GENERIC' AND status = 'LOCKED' AND deleted_at IS NULL",
                    Long.class
            )).as("independent Generic Event finalize-from-LOCKED and reopen-from-LOCKED targets")
                    .isGreaterThanOrEqualTo(2);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".events event_record "
                            + "JOIN " + schema + ".oratorios oratorio ON oratorio.id = event_record.id "
                            + "WHERE event_record.status = 'LOCKED' "
                            + "AND event_record.deleted_at IS NULL AND oratorio.deleted_at IS NULL",
                    Long.class
            )).as("independent Oratorio finalize-from-LOCKED and reopen-from-LOCKED targets")
                    .isGreaterThanOrEqualTo(2);
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidProductionSafeRbacPrerequisites")
    @DisplayName("REQ-DEV-FIXTURE-004/012 and REQ-RBAC-002/003 - invalid RBAC prerequisite -> fail before fixture mutation")
    void invalidProductionSafeRbacPrerequisiteShouldFailBeforeFixtureMutation(
            String scenario,
            String mutation
    ) {
        String schema = uniqueSchema("dev_fixture_rbac_preflight");
        migrate(schema, Map.of(), false).migrate();

        switch (mutation) {
            case "MISSING_PERMISSION" -> {
                jdbcTemplate.update(
                        "DELETE FROM " + schema + ".role_permissions relationship "
                                + "USING " + schema + ".permissions permission "
                                + "WHERE relationship.permission_id = permission.id "
                                + "AND permission.code = 'ORATORIANO_FORM_ATTACHMENT_GET'"
                );
                jdbcTemplate.update(
                        "DELETE FROM " + schema + ".permissions "
                                + "WHERE code = 'ORATORIANO_FORM_ATTACHMENT_GET'"
                );
            }
            case "DRIFTED_PERMISSION" -> jdbcTemplate.update(
                    "UPDATE " + schema + ".permissions "
                            + "SET label = 'Drifted fixture prerequisite' "
                            + "WHERE code = 'ORATORIANO_FORM_ATTACHMENT_GET'"
            );
            case "MISSING_BASELINE_BUNDLE_LINK" -> jdbcTemplate.update(
                    "DELETE FROM " + schema + ".role_permissions relationship "
                            + "USING " + schema + ".roles role_record, "
                            + schema + ".permissions permission "
                            + "WHERE relationship.role_id = role_record.id "
                            + "AND relationship.permission_id = permission.id "
                            + "AND role_record.name = 'COORD' "
                            + "AND permission.code = 'ORATORIANO_FORM_ATTACHMENT_GET'"
            );
            default -> throw new IllegalArgumentException("Unknown RBAC mutation: " + mutation);
        }

        assertThatThrownBy(() -> migrate(schema, validConfiguration(), true).migrate())
                .isInstanceOf(FlywayException.class);
        assertThat(canonicalAccountCount(schema)).isZero();
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-008 to REQ-DEV-FIXTURE-011 - Account through Presence endpoints -> ready")
    void accountMemberLocationEventAndPresenceEndpointPrerequisitesShouldExist() {
        String schema = uniqueSchema("dev_fixture_endpoint_core");
        migrate(schema, validConfiguration(), true).migrate();

        assertSoftly(softly -> {
            softly.assertThat(activeRoles(schema, "paulo.custom-role@example.com"))
                    .as("custom Role add target")
                    .doesNotContain("EVENT_SUPPORT");
            softly.assertThat(activeRoles(schema, "renata.custom-role@example.com"))
                    .as("custom Role drop target")
                    .contains("EVENT_SUPPORT");
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".accounts a "
                            + "WHERE a.email IN ('beatriz.registration@example.com', "
                            + "'fernanda.solicitation@example.com') "
                            + "AND NOT EXISTS (SELECT 1 FROM " + schema + ".members m "
                            + "WHERE m.account_id = a.id) "
                            + "AND NOT EXISTS (SELECT 1 FROM " + schema + ".membership_solicitations s "
                            + "WHERE s.account_id = a.id AND s.status = 'PENDING' AND s.deleted_at IS NULL)",
                    Long.class
            )).as("direct-registration and self-submission Accounts").isEqualTo(2);
            softly.assertThat(solicitationCount(schema, "PENDING")).isGreaterThanOrEqualTo(2);
            softly.assertThat(solicitationCount(schema, "APPROVED")).isGreaterThanOrEqualTo(1);
            softly.assertThat(solicitationCount(schema, "REJECTED")).isGreaterThanOrEqualTo(1);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".members m "
                            + "JOIN " + schema + ".accounts a ON a.id = m.account_id "
                            + "WHERE a.email = 'helena.inactive@example.com' "
                            + "AND m.status = 'INACTIVE' AND m.deleted_at IS NULL",
                    Long.class
            )).as("inactive Member activation target").isEqualTo(1);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".members m "
                            + "WHERE m.deleted_at IS NULL AND NOT EXISTS ("
                            + "SELECT 1 FROM " + schema + ".presences p "
                            + "WHERE p.member_id = m.id AND p.deleted_at IS NULL)",
                    Long.class
            )).as("Member with empty Presence history").isGreaterThanOrEqualTo(1);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".members m WHERE m.deleted_at IS NULL "
                            + "AND (SELECT COUNT(*) FROM " + schema + ".presences p "
                            + "WHERE p.member_id = m.id AND p.deleted_at IS NULL) > 1",
                    Long.class
            )).as("Member with multi-Event Presence history").isGreaterThanOrEqualTo(1);

            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".gam_locations "
                            + "WHERE id::text LIKE '01950000-0005-%' AND deleted_at IS NULL",
                    Long.class
            )).as("active ordinary GamLocations").isGreaterThanOrEqualTo(4);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".gam_locations "
                            + "WHERE id::text LIKE '01950000-0005-%' AND deleted_at IS NOT NULL",
                    Long.class
            )).as("soft-deleted ordinary GamLocation").isGreaterThanOrEqualTo(1);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".gam_locations l "
                            + "WHERE l.id::text LIKE '01950000-0005-%' AND l.deleted_at IS NULL "
                            + "AND NOT EXISTS (SELECT 1 FROM " + schema + ".events e "
                            + "WHERE e.gam_location_id = l.id)",
                    Long.class
            )).as("unreferenced ordinary update/removal targets").isGreaterThanOrEqualTo(2);

            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT COALESCE(p.code, 'PUBLIC')) "
                            + "FROM " + schema + ".events e "
                            + "LEFT JOIN " + schema + ".permissions p ON p.id = e.required_permission_id "
                            + "WHERE e.type = 'GENERIC' AND e.deleted_at IS NULL "
                            + "AND COALESCE(p.code, 'PUBLIC') IN "
                            + "('PUBLIC', 'EVENT_GET_MEMBER', 'EVENT_GET_COORD')",
                    Long.class
            )).as("public, Member, and Coordinator audiences").isEqualTo(3);
            softly.assertThat(eventStatuses(schema))
                    .contains("SCHEDULED", "COMPLETED", "LOCKED", "FINALIZED", "CANCELLED");
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".events e "
                            + "WHERE e.type = 'GENERIC' AND e.deleted_at IS NULL "
                            + "AND NOT EXISTS (SELECT 1 FROM " + schema + ".presences p "
                            + "WHERE p.event_id = e.id)",
                    Long.class
            )).as("Generic Event with no Presence history").isGreaterThanOrEqualTo(1);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".events e "
                            + "WHERE e.type = 'GENERIC' AND e.deleted_at IS NULL "
                            + "AND EXISTS (SELECT 1 FROM " + schema + ".presences p "
                            + "WHERE p.event_id = e.id AND p.deleted_at IS NULL)",
                    Long.class
            )).as("Generic Event blocked by active Presence").isGreaterThanOrEqualTo(1);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".events e "
                            + "WHERE e.type = 'GENERIC' AND e.deleted_at IS NULL "
                            + "AND EXISTS (SELECT 1 FROM " + schema + ".presences p "
                            + "WHERE p.event_id = e.id) "
                            + "AND NOT EXISTS (SELECT 1 FROM " + schema + ".presences p "
                            + "WHERE p.event_id = e.id AND p.deleted_at IS NULL)",
                    Long.class
            )).as("Generic Event with removed Presence history only").isGreaterThanOrEqualTo(1);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM (SELECT event_id FROM " + schema + ".presences "
                            + "WHERE deleted_at IS NULL GROUP BY event_id HAVING COUNT(*) > 1) roster",
                    Long.class
            )).as("multi-Member active roster").isGreaterThanOrEqualTo(1);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".presences "
                            + "WHERE deleted_at IS NULL AND observations IS NULL",
                    Long.class
            )).as("Presence without observations").isGreaterThanOrEqualTo(1);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".presences "
                            + "WHERE deleted_at IS NULL AND observations IS NOT NULL",
                    Long.class
            )).as("Presence with observations").isGreaterThanOrEqualTo(1);
        });
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-008 to REQ-DEV-FIXTURE-011 - Oratorio and form endpoints -> ready")
    void oratorioOratorianoAndAdditionalFormEndpointPrerequisitesShouldExist() {
        String schema = uniqueSchema("dev_fixture_endpoint_oratorio");
        migrate(schema, validConfiguration(), true).migrate();

        assertSoftly(softly -> {
            softly.assertThat(memberCount(schema, "ACTIVE")).isGreaterThan(50);
            softly.assertThat(nonDeletedOratorianoCount(schema)).isGreaterThan(60);
            softly.assertThat(oratorioStatuses(schema))
                    .contains("SCHEDULED", "COMPLETED", "LOCKED", "FINALIZED", "CANCELLED");
            softly.assertThat(jdbcTemplate.queryForList(
                    "SELECT DISTINCT team_type::text FROM " + schema + ".oratorio_team_assignments",
                    String.class
            )).containsExactlyInAnyOrder(
                    "LANCHE",
                    "GINCANA",
                    "BOA_TARDE_CRIANCAS",
                    "BOA_TARDE_JOVENS"
            );
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".oratorios o "
                            + "WHERE o.deleted_at IS NULL "
                            + "AND NOT EXISTS (SELECT 1 FROM " + schema + ".presences p "
                            + "WHERE p.event_id = o.event_id) "
                            + "AND NOT EXISTS (SELECT 1 FROM " + schema + ".oratoriano_attendances oa "
                            + "WHERE oa.oratorio_id = o.id)",
                    Long.class
            )).as("Oratorio with no attendance history").isGreaterThanOrEqualTo(1);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".oratorios o "
                            + "WHERE o.deleted_at IS NULL AND ("
                            + "EXISTS (SELECT 1 FROM " + schema + ".presences p "
                            + "WHERE p.event_id = o.event_id AND p.deleted_at IS NULL) "
                            + "OR EXISTS (SELECT 1 FROM " + schema + ".oratoriano_attendances oa "
                            + "WHERE oa.oratorio_id = o.id AND oa.deleted_at IS NULL))",
                    Long.class
            )).as("Oratorio blocked by active attendance").isGreaterThanOrEqualTo(1);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".oratorios o "
                            + "WHERE o.deleted_at IS NULL AND ("
                            + "EXISTS (SELECT 1 FROM " + schema + ".presences p "
                            + "WHERE p.event_id = o.event_id) "
                            + "OR EXISTS (SELECT 1 FROM " + schema + ".oratoriano_attendances oa "
                            + "WHERE oa.oratorio_id = o.id)) "
                            + "AND NOT EXISTS (SELECT 1 FROM " + schema + ".presences p "
                            + "WHERE p.event_id = o.event_id AND p.deleted_at IS NULL) "
                            + "AND NOT EXISTS (SELECT 1 FROM " + schema + ".oratoriano_attendances oa "
                            + "WHERE oa.oratorio_id = o.id AND oa.deleted_at IS NULL)",
                    Long.class
            )).as("Oratorio with removed attendance history only").isGreaterThanOrEqualTo(1);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".oratorios o "
                            + "WHERE EXISTS (SELECT 1 FROM " + schema + ".presences p "
                            + "WHERE p.event_id = o.event_id AND p.deleted_at IS NULL) "
                            + "AND EXISTS (SELECT 1 FROM " + schema + ".oratoriano_attendances oa "
                            + "WHERE oa.oratorio_id = o.id AND oa.deleted_at IS NULL)",
                    Long.class
            )).as("combined Member and Oratoriano attendance").isGreaterThanOrEqualTo(1);

            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".oratorianos o "
                            + "WHERE o.deleted_at IS NULL AND o.birth_date IS NULL "
                            + "AND o.phone_number IS NULL",
                    Long.class
            )).as("minimal Oratoriano").isGreaterThanOrEqualTo(1);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".oratorianos o "
                            + "WHERE o.deleted_at IS NULL AND o.birth_date IS NOT NULL "
                            + "AND o.phone_number IS NOT NULL",
                    Long.class
            )).as("enriched Oratoriano").isGreaterThanOrEqualTo(1);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".oratorianos o "
                            + "WHERE o.deleted_at IS NULL AND NOT EXISTS ("
                            + "SELECT 1 FROM " + schema + ".oratoriano_attendances oa "
                            + "WHERE oa.oratoriano_id = o.id AND oa.deleted_at IS NULL)",
                    Long.class
            )).as("Oratoriano without attendance").isGreaterThanOrEqualTo(1);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".oratorianos o "
                            + "WHERE o.deleted_at IS NULL AND (SELECT COUNT(*) "
                            + "FROM " + schema + ".oratoriano_attendances oa "
                            + "WHERE oa.oratoriano_id = o.id AND oa.deleted_at IS NULL) > 1",
                    Long.class
            )).as("Oratoriano with multi-period attendance").isGreaterThanOrEqualTo(1);

            softly.assertThat(formStatuses(schema))
                    .contains("DRAFT", "COMPLETED", "SUPERSEDED", "REVOKED");
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM (SELECT oratoriano_id FROM "
                            + schema + ".oratoriano_additional_forms "
                            + "WHERE deleted_at IS NULL GROUP BY oratoriano_id HAVING COUNT(*) > 1) history",
                    Long.class
            )).as("multi-version form history").isGreaterThanOrEqualTo(1);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".oratoriano_additional_forms f "
                            + "WHERE f.status = 'DRAFT' AND f.deleted_at IS NULL "
                            + "AND EXISTS (SELECT 1 FROM " + schema + ".oratoriano_form_print_snapshots s "
                            + "WHERE s.form_id = f.id AND s.draft_revision = f.draft_revision "
                            + "AND s.deleted_at IS NULL) "
                            + "AND EXISTS (SELECT 1 FROM " + schema + ".oratoriano_form_attachments a "
                            + "WHERE a.form_id = f.id AND a.deleted_at IS NULL)",
                    Long.class
            )).as("completion-ready form with current snapshot and attachment")
                    .isGreaterThanOrEqualTo(1);
            softly.assertThat(tableCount(schema, "oratoriano_form_print_snapshots"))
                    .isGreaterThanOrEqualTo(2);
            softly.assertThat(tableCount(schema, "oratoriano_form_attachments"))
                    .isGreaterThanOrEqualTo(1);
        });
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-008 - search dataset -> pagination and name variation")
    void searchDatasetShouldProvidePaginationAndNameVariation() {
        String schema = uniqueSchema("dev_fixture_search_variation");
        migrate(schema, validConfiguration(), true).migrate();

        assertSoftly(softly -> {
            softly.assertThat(memberCount(schema, "ACTIVE")).isGreaterThan(50);
            softly.assertThat(nonDeletedOratorianoCount(schema)).isGreaterThan(60);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".members "
                            + "WHERE first_name ~ '[\u00c1-\u00ff]' OR surname ~ '[\u00c1-\u00ff]'",
                    Long.class
            )).as("accented Member names").isGreaterThanOrEqualTo(1);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".members WHERE surname LIKE '%-%'",
                    Long.class
            )).as("hyphenated Member names").isGreaterThanOrEqualTo(1);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".members WHERE surname LIKE '%''%'",
                    Long.class
            )).as("apostrophe-bearing Member names").isGreaterThanOrEqualTo(1);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT status::text) FROM " + schema + ".members "
                            + "WHERE deleted_at IS NULL",
                    Long.class
            )).as("Member lifecycle variation").isEqualTo(2);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT type::text) FROM " + schema + ".events "
                            + "WHERE deleted_at IS NULL",
                    Long.class
            )).as("Generic and Oratorio type variation").isEqualTo(2);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT status::text) FROM " + schema + ".events "
                            + "WHERE deleted_at IS NULL",
                    Long.class
            )).as("Event lifecycle variation").isEqualTo(5);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".presences "
                            + "WHERE deleted_at IS NULL AND observations IS NULL",
                    Long.class
            )).isGreaterThanOrEqualTo(1);
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".presences "
                            + "WHERE deleted_at IS NULL AND observations IS NOT NULL",
                    Long.class
            )).isGreaterThanOrEqualTo(1);
        });
    }

    private static Stream<Arguments> invalidConfiguration() {
        return Stream.of(
                Arguments.of(
                        "missing execution marker",
                        Map.of(PASSWORD_HASH_PLACEHOLDER, VALID_PASSWORD_HASH)
                ),
                Arguments.of(
                        "false execution marker",
                        Map.of(
                                EXECUTION_MARKER_PLACEHOLDER, "false",
                                PASSWORD_HASH_PLACEHOLDER, VALID_PASSWORD_HASH
                        )
                ),
                Arguments.of(
                        "blank execution marker",
                        Map.of(
                                EXECUTION_MARKER_PLACEHOLDER, " ",
                                PASSWORD_HASH_PLACEHOLDER, VALID_PASSWORD_HASH
                        )
                ),
                Arguments.of(
                        "malformed execution marker",
                        Map.of(
                                EXECUTION_MARKER_PLACEHOLDER, "yes",
                                PASSWORD_HASH_PLACEHOLDER, VALID_PASSWORD_HASH
                        )
                ),
                Arguments.of(
                        "missing password hash",
                        Map.of(EXECUTION_MARKER_PLACEHOLDER, "true")
                ),
                Arguments.of(
                        "blank password hash",
                        Map.of(
                                EXECUTION_MARKER_PLACEHOLDER, "true",
                                PASSWORD_HASH_PLACEHOLDER, " "
                        )
                ),
                Arguments.of(
                        "malformed password hash",
                        Map.of(
                                EXECUTION_MARKER_PLACEHOLDER, "true",
                                PASSWORD_HASH_PLACEHOLDER, "not-a-delegated-pbkdf2-hash"
                        )
                ),
                Arguments.of(
                        "unsupported delegated password hash",
                        Map.of(
                                EXECUTION_MARKER_PLACEHOLDER, "true",
                                PASSWORD_HASH_PLACEHOLDER, "{bcrypt}$2a$10$unsupported"
                        )
                )
        );
    }

    private static Stream<Arguments> invalidProductionSafeRbacPrerequisites() {
        return Stream.of(
                Arguments.of("missing required Permission", "MISSING_PERMISSION"),
                Arguments.of("drifted required Permission metadata", "DRIFTED_PERMISSION"),
                Arguments.of(
                        "missing accepted COORD role-permission bundle link",
                        "MISSING_BASELINE_BUNDLE_LINK"
                )
        );
    }

    private static Map<String, String> validConfiguration() {
        return Map.of(
                EXECUTION_MARKER_PLACEHOLDER, "true",
                PASSWORD_HASH_PLACEHOLDER, VALID_PASSWORD_HASH
        );
    }

    private Flyway migrate(
            String schema,
            Map<String, String> placeholders,
            boolean includeDevelopmentFixtures
    ) {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations(includeDevelopmentFixtures
                        ? new String[]{"classpath:db/migration", "classpath:db/dev-migration"}
                        : new String[]{"classpath:db/migration"})
                .javaMigrations(
                        new R__SeedPermissionsAndRoles(),
                        new R__SeedOratorioGamLocation()
                )
                .placeholders(placeholders);
        return configuration.load();
    }

    private long canonicalAccountCount(String schema) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".accounts WHERE email IN ("
                        + canonicalPersonaParameters() + ")",
                Long.class,
                CANONICAL_PERSONA_EMAILS.toArray()
        );
    }

    private long canonicalAccountCountWhenTableExists(String schema) {
        Long tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = ? AND table_name = 'accounts'",
                Long.class,
                schema
        );
        return tableCount == 0 ? 0 : canonicalAccountCount(schema);
    }

    private Map<String, UUID> canonicalAccountIds(String schema) {
        return jdbcTemplate.query(
                "SELECT email, id FROM " + schema + ".accounts WHERE email IN ("
                        + canonicalPersonaParameters() + ") ORDER BY email",
                statement -> {
                    int index = 1;
                    for (String email : CANONICAL_PERSONA_EMAILS) {
                        statement.setString(index++, email);
                    }
                },
                resultSet -> {
                    Map<String, UUID> identities = new LinkedHashMap<>();
                    while (resultSet.next()) {
                        identities.put(
                                resultSet.getString("email"),
                                resultSet.getObject("id", UUID.class)
                        );
                    }
                    return identities;
                }
        );
    }

    private Map<String, UUID> canonicalRoleAssignmentIds(String schema) {
        return jdbcTemplate.query(
                "SELECT a.email, r.name, ar.id FROM " + schema + ".account_roles ar "
                        + "JOIN " + schema + ".accounts a ON a.id = ar.account_id "
                        + "JOIN " + schema + ".roles r ON r.id = ar.role_id "
                        + "WHERE a.email IN (" + canonicalPersonaParameters() + ") "
                        + "AND ar.deleted_at IS NULL ORDER BY a.email, r.name",
                statement -> {
                    int index = 1;
                    for (String email : CANONICAL_PERSONA_EMAILS) {
                        statement.setString(index++, email);
                    }
                },
                resultSet -> {
                    Map<String, UUID> identities = new LinkedHashMap<>();
                    while (resultSet.next()) {
                        identities.put(
                                resultSet.getString("email") + ":" + resultSet.getString("name"),
                                resultSet.getObject("id", UUID.class)
                        );
                    }
                    return identities;
                }
        );
    }

    private List<Map<String, Object>> canonicalAuditSnapshot(String schema) {
        return jdbcTemplate.queryForList(
                "SELECT email, id, password_hash, display_name, created_at, created_by, "
                        + "updated_at, updated_by, deleted_at, deleted_by "
                        + "FROM " + schema + ".accounts WHERE email IN ("
                        + canonicalPersonaParameters() + ") ORDER BY email",
                CANONICAL_PERSONA_EMAILS.toArray()
        );
    }

    private Set<String> activeRoles(String schema, String email) {
        return Set.copyOf(jdbcTemplate.queryForList(
                "SELECT r.name FROM " + schema + ".account_roles ar "
                        + "JOIN " + schema + ".accounts a ON a.id = ar.account_id "
                        + "JOIN " + schema + ".roles r ON r.id = ar.role_id "
                        + "WHERE a.email = ? AND ar.deleted_at IS NULL AND r.deleted_at IS NULL",
                String.class,
                email
        ));
    }

    private Set<String> activePermissions(String schema, String roleName) {
        return Set.copyOf(jdbcTemplate.queryForList(
                "SELECT p.code FROM " + schema + ".role_permissions rp "
                        + "JOIN " + schema + ".roles r ON r.id = rp.role_id "
                        + "JOIN " + schema + ".permissions p ON p.id = rp.permission_id "
                        + "WHERE r.name = ? AND r.deleted_at IS NULL "
                        + "AND rp.deleted_at IS NULL AND p.deleted_at IS NULL",
                String.class,
                roleName
        ));
    }

    private long activeRoleCount(String schema, String roleName) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".roles WHERE name = ? AND deleted_at IS NULL",
                Long.class,
                roleName
        );
    }

    private long softDeletedRoleCount(String schema, String roleName) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".roles WHERE name = ? AND deleted_at IS NOT NULL",
                Long.class,
                roleName
        );
    }

    private long memberCount(String schema, String status) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".members "
                        + "WHERE status::text = ? AND deleted_at IS NULL",
                Long.class,
                status
        );
    }

    private long nonDeletedOratorianoCount(String schema) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".oratorianos WHERE deleted_at IS NULL",
                Long.class
        );
    }

    private long softDeletedOratorianoCount(String schema) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".oratorianos WHERE deleted_at IS NOT NULL",
                Long.class
        );
    }

    private long solicitationCount(String schema, String status) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".membership_solicitations "
                        + "WHERE status::text = ? AND deleted_at IS NULL",
                Long.class,
                status
        );
    }

    private Set<String> eventStatuses(String schema) {
        return Set.copyOf(jdbcTemplate.queryForList(
                "SELECT DISTINCT status::text FROM " + schema + ".events WHERE deleted_at IS NULL",
                String.class
        ));
    }

    private Set<String> oratorioStatuses(String schema) {
        return Set.copyOf(jdbcTemplate.queryForList(
                "SELECT DISTINCT e.status::text FROM " + schema + ".oratorios o "
                        + "JOIN " + schema + ".events e ON e.id = o.event_id "
                        + "WHERE o.deleted_at IS NULL AND e.deleted_at IS NULL",
                String.class
        ));
    }

    private Set<String> formStatuses(String schema) {
        return Set.copyOf(jdbcTemplate.queryForList(
                "SELECT DISTINCT status::text FROM " + schema + ".oratoriano_additional_forms "
                        + "WHERE deleted_at IS NULL",
                String.class
        ));
    }

    private long activityCount(String schema) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".activity_logs",
                Long.class
        );
    }

    private long tableCount(String schema, String table) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + "." + table,
                Long.class
        );
    }

    private Set<String> manifestIdentitySnapshot(String schema) {
        Set<String> identities = new LinkedHashSet<>();
        List<String> idTables = List.of(
                "accounts",
                "roles",
                "role_permissions",
                "account_roles",
                "members",
                "gam_locations",
                "events",
                "oratorios",
                "membership_solicitations",
                "presences",
                "oratorianos",
                "oratoriano_attendances",
                "oratoriano_additional_forms",
                "oratoriano_form_print_snapshots",
                "oratoriano_form_attachments"
        );
        for (String table : idTables) {
            jdbcTemplate.queryForList(
                    "SELECT id::text FROM " + schema + "." + table
                            + " WHERE id::text LIKE '01950000-%' ORDER BY id",
                    String.class
            ).forEach(id -> identities.add(table + ":" + id));
        }
        jdbcTemplate.queryForList(
                "SELECT oratorio_id::text || ':' || member_id::text || ':' || team_type::text "
                        + "FROM " + schema + ".oratorio_team_assignments "
                        + "ORDER BY oratorio_id, member_id, team_type",
                String.class
        ).forEach(key -> identities.add("oratorio_team_assignments:" + key));
        return identities;
    }

    private Map<String, String> fixtureTableDigests(String schema) {
        Map<String, String> orders = Map.ofEntries(
                Map.entry("accounts", "t.id"),
                Map.entry("roles", "t.id"),
                Map.entry("permissions", "t.id"),
                Map.entry("role_permissions", "t.id"),
                Map.entry("account_roles", "t.id"),
                Map.entry("members", "t.id"),
                Map.entry("gam_locations", "t.id"),
                Map.entry("events", "t.id"),
                Map.entry("presences", "t.id"),
                Map.entry("oratorios", "t.id"),
                Map.entry(
                        "oratorio_team_assignments",
                        "t.oratorio_id, t.member_id, t.team_type"
                ),
                Map.entry("membership_solicitations", "t.id"),
                Map.entry("oratorianos", "t.id"),
                Map.entry("oratoriano_attendances", "t.id"),
                Map.entry("oratoriano_additional_forms", "t.id"),
                Map.entry("oratoriano_form_print_snapshots", "t.id"),
                Map.entry("oratoriano_form_attachments", "t.id"),
                Map.entry("activity_logs", "t.id")
        );
        Map<String, String> digests = new LinkedHashMap<>();
        orders.forEach((table, order) -> digests.put(
                table,
                jdbcTemplate.queryForObject(
                        "SELECT md5(COALESCE(string_agg(row_to_json(t)::text, "
                                + "E'\\n' ORDER BY " + order + "), '')) "
                                + "FROM " + schema + "." + table + " t",
                        String.class
                )
        ));
        return digests;
    }

    private UUID requiredAccountId(String schema, String email) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM " + schema + ".accounts WHERE email = ? AND deleted_at IS NULL",
                UUID.class,
                email
        );
    }

    private static String normalizedPdfText(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return Normalizer.normalize(
                            new PDFTextStripper().getText(document),
                            Normalizer.Form.NFD
                    )
                    .replaceAll("\\p{M}", "")
                    .toLowerCase(Locale.ROOT);
        }
    }

    private static String canonicalPersonaParameters() {
        return String.join(",", CANONICAL_PERSONA_EMAILS.stream().map(ignored -> "?").toList());
    }

    private static PasswordEncoder passwordEncoder() {
        return new SecurityConfig(null, null, null).passwordEncoder();
    }

    private static DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private static String uniqueSchema(String prefix) {
        String boundedPrefix = prefix.substring(0, Math.min(prefix.length(), 30));
        return boundedPrefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
