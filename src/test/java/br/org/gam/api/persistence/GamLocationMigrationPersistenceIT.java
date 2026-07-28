package br.org.gam.api.persistence;

import br.org.gam.api.db.migration.R__SeedPermissionsAndRoles;
import br.org.gam.api.db.migration.R__SynchronizeSystemGamLocations;
import br.org.gam.api.gamLocation.application.SystemGamLocationCatalog;
import br.org.gam.api.gamLocation.application.SystemGamLocationCatalog.Entry;
import br.org.gam.api.gamLocation.application.SystemGamLocationCatalog.Lifecycle;
import br.org.gam.api.gamLocation.application.ValidateSystemGamLocationCatalog;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.PersistenceTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.DefaultApplicationArguments;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@PersistenceTest
@DisplayName("Persistence - GamLocation schema migration")
class GamLocationMigrationPersistenceIT {

    private static final String DEVELOPMENT_FIXTURE_PASSWORD = UUID.randomUUID().toString();
    private static final String DEVELOPMENT_FIXTURE_PASSWORD_HASH = passwordEncoder().encode(
            DEVELOPMENT_FIXTURE_PASSWORD
    );

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine")
    );

    static {
        POSTGRES.start();
    }

    private final DataSource dataSource = dataSource();
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

    @Test
    @DisplayName("REQ-GAM-LOCATION-007 and ADR-0009 - V22 legacy-row backfill -> accent-insensitive canonical identity")
    void v22ShouldBackfillLegacyRowsWithCanonicalDuplicateIdentity() {
        String schema = uniqueSchema("gam_location_backfill");
        migrate(schema, "21", "classpath:db/migration").migrate();

        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".locations "
                        + "(id, name, street, city, state, postal_code, country_code, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                "Sal\u00e3o  S\u00e3o Jos\u00e9",
                "Rua S\u00e3o Jos\u00e9, 123",
                "S\u00e3o Paulo",
                "S\u00e3o Paulo",
                "01000-000",
                "BR",
                now,
                now
        );

        migrate(schema, null, "classpath:db/migration").migrate();

        Map<String, Object> identity = jdbcTemplate.queryForMap(
                "SELECT identity_name, identity_street, identity_city, identity_state, "
                        + "identity_postal_code, identity_country_code "
                        + "FROM " + schema + ".gam_locations WHERE id = ?",
                id
        );
        assertThat(identity)
                .containsEntry("identity_name", "salao sao jose")
                .containsEntry("identity_street", "rua sao jose, 123")
                .containsEntry("identity_city", "sao paulo")
                .containsEntry("identity_state", "sao paulo")
                .containsEntry("identity_postal_code", "01000-000")
                .containsEntry("identity_country_code", "br");
    }

    @Test
    @DisplayName("REQ-EVENT-004 - V22 Event schema -> GamLocation foreign key is required")
    void v22ShouldRequireEveryEventToReferenceAGamLocation() {
        String schema = uniqueSchema("event_gam_location_required");
        migrate(schema, null, "classpath:db/migration").migrate();

        String isNullable = jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name = 'events' AND column_name = 'gam_location_id'",
                String.class,
                schema
        );

        assertThat(isNullable).isEqualTo("NO");
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-005 - development Flyway callback -> canonical GamLocation fixtures")
    void developmentCallbackShouldUseCanonicalGamLocationSchema() {
        String schema = uniqueSchema("gam_location_dev");
        Flyway flyway = migrate(
                schema,
                null,
                "classpath:db/migration",
                "classpath:db/dev-migration"
        );

        flyway.migrate();
        flyway.migrate();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".gam_locations WHERE name IN (?, ?)",
                Long.class,
                "Sede Principal GAM",
                "Sal\u00e3o de Eventos Anexo"
        )).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForMap(
                "SELECT identity_name, identity_street, identity_city, identity_country_code "
                        + "FROM " + schema + ".gam_locations WHERE name = 'Sede Principal GAM'"
        )).containsEntry("identity_name", "sede principal gam")
                .containsEntry("identity_street", "rua ficticia, 123")
                .containsEntry("identity_city", "sao paulo")
                .containsEntry("identity_country_code", "br");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".events "
                        + "WHERE title IN (?, ?, ?) AND gam_location_id IS NOT NULL",
                Long.class,
                "Reuni\u00e3o de Coordenadores",
                "Encontro Semanal GAM",
                "Palestra sobre Voluntariado (Passado)"
        )).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".events WHERE gam_location_id IS NULL",
                Long.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".events "
                        + "WHERE title = 'Evento Portas Abertas' AND gam_location_id IS NOT NULL",
                Long.class
        )).isEqualTo(1L);
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-CATALOG-001, REQ-GAM-LOCATION-CATALOG-002 and REQ-DATA-003 - repeated synchronization -> stable UUID v7 catalog and complete no-op")
    void repeatedSystemCatalogSynchronizationShouldBeACompleteNoOp() {
        String schema = uniqueSchema("system_gam_location_noop");
        Flyway flyway = migrateSystemCatalog(schema);

        flyway.migrate();
        List<Map<String, Object>> before = systemCatalogSnapshot(schema);

        assertThat(before).hasSize(3);
        assertThat(before)
                .extracting(row -> row.get("code"))
                .containsExactly("DBA", "DBCA", "DBSM");
        assertThat(before).allSatisfy(row -> {
            assertThat(row)
                    .containsEntry("system_managed", true)
                    .containsEntry("catalog_current", true)
                    .containsEntry("created_by", null)
                    .containsEntry("updated_by", null);
            assertThat(((UUID) row.get("id")).version()).isEqualTo(7);
        });
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".accounts",
                Long.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".activity_logs",
                Long.class
        )).isZero();

        forceSystemCatalogRerun(schema, flyway);

        assertThat(systemCatalogSnapshot(schema)).isEqualTo(before);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".activity_logs",
                Long.class
        )).isZero();
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-CATALOG-005 and REQ-GAM-LOCATION-CATALOG-007 - soft-deleted drift -> restore accepted metadata and duplicate keys under preserved UUID")
    void synchronizationShouldRestoreAndReconcileAUniqueSystemLocation() {
        String schema = uniqueSchema("system_gam_location_restore");
        Flyway flyway = migrateSystemCatalog(schema);
        flyway.migrate();
        UUID originalId = jdbcTemplate.queryForObject(
                "SELECT id FROM " + schema + ".gam_locations WHERE code = 'DBA'",
                UUID.class
        );
        Timestamp driftedAt = Timestamp.from(Instant.parse("2000-01-01T00:00:00Z"));
        jdbcTemplate.update(
                "UPDATE " + schema + ".gam_locations SET "
                        + "name = 'Outdated Assuncao', street = 'Outdated street', "
                        + "identity_name = 'outdated assuncao', identity_street = 'outdated street', "
                        + "deleted_at = ?, deleted_by = NULL, updated_at = ? WHERE code = 'DBA'",
                driftedAt,
                driftedAt
        );

        forceSystemCatalogRerun(schema, flyway);

        Map<String, Object> reconciled = jdbcTemplate.queryForMap(
                "SELECT id, name, street, identity_name, identity_street, deleted_at, updated_at "
                        + "FROM " + schema + ".gam_locations WHERE code = 'DBA'"
        );
        assertThat(reconciled)
                .containsEntry("id", originalId)
                .containsEntry("name", "Dom Bosco Assunção")
                .containsEntry("street", "Rua Boa Morte, 1835 - Centro")
                .containsEntry("identity_name", "dom bosco assuncao")
                .containsEntry("identity_street", "rua boa morte, 1835 - centro")
                .containsEntry("deleted_at", null);
        assertThat((Timestamp) reconciled.get("updated_at")).isAfter(driftedAt);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".gam_locations WHERE code = 'DBA'",
                Long.class
        )).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".activity_logs",
                Long.class
        )).isZero();
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-CATALOG-006 and REQ-DATA-004 - ordinary accepted-identity collision -> atomic synchronization failure without adoption")
    void ordinaryDuplicateIdentityShouldBlockTheCompleteSystemCatalogSynchronization() {
        String schema = uniqueSchema("system_gam_location_collision");
        migrate(schema, "33", "classpath:db/migration").migrate();
        UUID ordinaryId = insertOrdinaryDbcaDuplicate(schema);

        Flyway flyway = migrateSystemCatalog(schema);

        assertThatThrownBy(flyway::migrate)
                .hasStackTraceContaining(
                        "Reserved duplicate identity for DBCA belongs to another GamLocation"
                );
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".gam_locations WHERE system_managed = TRUE",
                Long.class
        )).isZero();
        assertThat(jdbcTemplate.queryForMap(
                "SELECT id, code, system_managed, catalog_current, name "
                        + "FROM " + schema + ".gam_locations WHERE id = ?",
                ordinaryId
        )).containsEntry("id", ordinaryId)
                .containsEntry("code", null)
                .containsEntry("system_managed", false)
                .containsEntry("catalog_current", false)
                .containsEntry("name", "Dom Bosco Cidade Alta");
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-CATALOG-006 and REQ-DATA-004 - soft-deleted ordinary accepted-identity collision -> atomic synchronization failure")
    void softDeletedOrdinaryDuplicateIdentityShouldBlockTheCompleteSystemCatalogSynchronization() {
        String schema = uniqueSchema("soft_deleted_collision");
        migrate(schema, "33", "classpath:db/migration").migrate();
        UUID ordinaryId = insertOrdinaryDbcaDuplicate(schema);
        Timestamp deletedAt = Timestamp.from(Instant.parse("2000-02-03T04:05:06Z"));
        jdbcTemplate.update(
                "UPDATE " + schema + ".gam_locations SET deleted_at = ? WHERE id = ?",
                deletedAt,
                ordinaryId
        );

        Flyway flyway = migrateSystemCatalog(schema);

        assertThatThrownBy(flyway::migrate)
                .hasStackTraceContaining(
                        "Reserved duplicate identity for DBCA belongs to another GamLocation"
                );
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".gam_locations WHERE system_managed = TRUE",
                Long.class
        )).as("synchronization is atomic").isZero();
        assertThat(jdbcTemplate.queryForMap(
                "SELECT id, code, system_managed, catalog_current, deleted_at "
                        + "FROM " + schema + ".gam_locations WHERE id = ?",
                ordinaryId
        )).containsEntry("id", ordinaryId)
                .containsEntry("code", null)
                .containsEntry("system_managed", false)
                .containsEntry("catalog_current", false)
                .containsEntry("deleted_at", deletedAt);
    }

    @ParameterizedTest(name = "configured code [{0}]")
    @ValueSource(strings = {" ", "UNKNOWN"})
    @DisplayName("REQ-GAM-LOCATION-CATALOG-008 - blank or unknown Oratorio location code -> startup validation failure")
    void startupValidationShouldRejectBlankOrUnknownConfiguredLocation(String configuredCode) {
        String schema = uniqueSchema("invalid_oratorio_location_code");
        migrateSystemCatalog(schema).migrate();
        ValidateSystemGamLocationCatalog validator = new ValidateSystemGamLocationCatalog(
                new JdbcTemplate(dataSource(schema)),
                configuredCode
        );

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "gam.oratorio.location-code must be one exact current system location code"
                );
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-CATALOG-008 - retired Oratorio location code -> startup validation failure")
    void startupValidationShouldRejectRetiredConfiguredLocation() {
        String schema = uniqueSchema("retired_oratorio_location_code");
        migrateSystemCatalog(schema).migrate();
        ValidateSystemGamLocationCatalog validator = new ValidateSystemGamLocationCatalog(
                new JdbcTemplate(dataSource(schema)),
                "DBA"
        );
        List<Entry> retiredCatalog = catalogWithLifecycle("DBA", Lifecycle.RETIRED);

        try (MockedStatic<SystemGamLocationCatalog> ignored = mockCatalog(retiredCatalog)) {
            assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments(new String[0])))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(
                            "gam.oratorio.location-code must be one exact current system location code"
                    );
        }
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-CATALOG-005 and REQ-DATA-006 - persisted metadata drift -> startup validation fails without hidden repair")
    void startupValidationShouldRejectPersistedDriftWithoutMutation() {
        String schema = uniqueSchema("system_gam_location_validation");
        migrateSystemCatalog(schema).migrate();
        Timestamp driftedAt = Timestamp.from(Instant.parse("2001-02-03T04:05:06Z"));
        jdbcTemplate.update(
                "UPDATE " + schema + ".gam_locations "
                        + "SET name = 'Drifted DBA', updated_at = ? WHERE code = 'DBA'",
                driftedAt
        );
        JdbcTemplate schemaJdbcTemplate = new JdbcTemplate(dataSource(schema));
        ValidateSystemGamLocationCatalog validator =
                new ValidateSystemGamLocationCatalog(schemaJdbcTemplate, "DBSM");

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Application-owned metadata drifted for DBA");
        assertThat(jdbcTemplate.queryForMap(
                "SELECT name, updated_at FROM " + schema + ".gam_locations WHERE code = 'DBA'"
        )).containsEntry("name", "Drifted DBA")
                .containsEntry("updated_at", driftedAt);
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-CATALOG-008 and REQ-DATA-006 - soft-deleted configured system location with unchanged checksum -> startup failure without restoration")
    void startupValidationShouldRejectSoftDeletedConfiguredLocationWithoutMutation() {
        String schema = uniqueSchema("system_gam_location_configured");
        migrateSystemCatalog(schema).migrate();
        Timestamp deletedAt = Timestamp.from(Instant.parse("2002-03-04T05:06:07Z"));
        jdbcTemplate.update(
                "UPDATE " + schema + ".gam_locations "
                        + "SET deleted_at = ?, deleted_by = NULL WHERE code = 'DBSM'",
                deletedAt
        );
        JdbcTemplate schemaJdbcTemplate = new JdbcTemplate(dataSource(schema));
        ValidateSystemGamLocationCatalog validator =
                new ValidateSystemGamLocationCatalog(schemaJdbcTemplate, "DBSM");

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Current system location DBSM is soft-deleted");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM " + schema + ".gam_locations WHERE code = 'DBSM'",
                Timestamp.class
        )).isEqualTo(deletedAt);
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-CATALOG-003/007 and REQ-DATA-005 - current to RETIRED transition -> preserve an active row and historical Event embedding")
    void retirementShouldRestoreSoftDeletedSystemLocationForHistoricalEmbedding() {
        String schema = uniqueSchema("system_retirement");
        migrateSystemCatalog(schema).migrate();
        UUID originalId = jdbcTemplate.queryForObject(
                "SELECT id FROM " + schema + ".gam_locations WHERE code = 'DBA'",
                UUID.class
        );
        UUID historicalEventId = insertHistoricalEvent(schema, originalId);
        Timestamp deletedAt = Timestamp.from(Instant.parse("2003-04-05T06:07:08Z"));
        jdbcTemplate.update(
                "UPDATE " + schema + ".gam_locations "
                        + "SET deleted_at = ?, deleted_by = NULL WHERE id = ?",
                deletedAt,
                originalId
        );

        List<Entry> retiredCatalog = catalogWithLifecycle("DBA", Lifecycle.RETIRED);
        try (MockedStatic<SystemGamLocationCatalog> ignored = mockCatalog(retiredCatalog)) {
            migrateSystemCatalog(schema).migrate();
        }

        assertThat(jdbcTemplate.queryForMap(
                "SELECT id, code, system_managed, catalog_current, deleted_at "
                        + "FROM " + schema + ".gam_locations WHERE id = ?",
                originalId
        )).containsEntry("id", originalId)
                .containsEntry("code", "DBA")
                .containsEntry("system_managed", true)
                .containsEntry("catalog_current", false)
                .containsEntry("deleted_at", null);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".events event "
                        + "JOIN " + schema + ".gam_locations location "
                        + "ON location.id = event.gam_location_id AND location.deleted_at IS NULL "
                        + "WHERE event.id = ?",
                Long.class,
                historicalEventId
        )).as("historical Event retains an embeddable retired GamLocation")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-CATALOG-005/007 and REQ-DATA-006 - soft-deleted RETIRED row -> startup validation fails without hidden repair")
    void startupValidationShouldRejectSoftDeletedRetiredSystemLocation() {
        String schema = uniqueSchema("system_retired_validation");
        migrateSystemCatalog(schema).migrate();
        Timestamp deletedAt = Timestamp.from(Instant.parse("2004-05-06T07:08:09Z"));
        jdbcTemplate.update(
                "UPDATE " + schema + ".gam_locations "
                        + "SET catalog_current = FALSE, deleted_at = ?, deleted_by = NULL "
                        + "WHERE code = 'DBA'",
                deletedAt
        );
        JdbcTemplate schemaJdbcTemplate = new JdbcTemplate(dataSource(schema));
        ValidateSystemGamLocationCatalog validator =
                new ValidateSystemGamLocationCatalog(schemaJdbcTemplate, "DBSM");
        List<Entry> retiredCatalog = catalogWithLifecycle("DBA", Lifecycle.RETIRED);

        try (MockedStatic<SystemGamLocationCatalog> ignored = mockCatalog(retiredCatalog)) {
            assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments(new String[0])))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DBA")
                    .hasMessageContaining("soft-deleted");
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM " + schema + ".gam_locations WHERE code = 'DBA'",
                Timestamp.class
        )).isEqualTo(deletedAt);
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-CATALOG-007 and REQ-DATA-005 - RETIRED code becomes CURRENT again -> preserved UUID is restored and reactivated")
    void reactivationShouldReuseAndRestoreTheRetiredSystemLocation() {
        String schema = uniqueSchema("system_reactivation");
        migrateSystemCatalog(schema).migrate();
        UUID originalId = jdbcTemplate.queryForObject(
                "SELECT id FROM " + schema + ".gam_locations WHERE code = 'DBA'",
                UUID.class
        );
        List<Entry> retiredCatalog = catalogWithLifecycle("DBA", Lifecycle.RETIRED);
        try (MockedStatic<SystemGamLocationCatalog> ignored = mockCatalog(retiredCatalog)) {
            migrateSystemCatalog(schema).migrate();
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT catalog_current FROM " + schema + ".gam_locations WHERE id = ?",
                Boolean.class,
                originalId
        )).isFalse();

        Timestamp deletedAt = Timestamp.from(Instant.parse("2005-06-07T08:09:10Z"));
        jdbcTemplate.update(
                "UPDATE " + schema + ".gam_locations "
                        + "SET deleted_at = ?, deleted_by = NULL WHERE id = ?",
                deletedAt,
                originalId
        );

        migrateSystemCatalog(schema).migrate();

        assertThat(jdbcTemplate.queryForMap(
                "SELECT id, code, system_managed, catalog_current, deleted_at "
                        + "FROM " + schema + ".gam_locations WHERE code = 'DBA'"
        )).containsEntry("id", originalId)
                .containsEntry("code", "DBA")
                .containsEntry("system_managed", true)
                .containsEntry("catalog_current", true)
                .containsEntry("deleted_at", null);
    }

    private Flyway migrate(String schema, String target, String... locations) {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations(locations);
        boolean includesDevelopmentFixtures = false;
        for (String location : locations) {
            includesDevelopmentFixtures |= location.equals("classpath:db/dev-migration");
        }
        if (includesDevelopmentFixtures) {
            configuration.javaMigrations(
                    new R__SeedPermissionsAndRoles(),
                    new R__SynchronizeSystemGamLocations()
            ).placeholders(Map.of(
                    "gamDevFixtureExecutionEnabled", "true",
                    "gamDevFixturePasswordHash", DEVELOPMENT_FIXTURE_PASSWORD_HASH
            ));
        } else {
            configuration.javaMigrations(new R__SeedPermissionsAndRoles());
        }
        if (target != null) {
            configuration.target(MigrationVersion.fromVersion(target));
        }
        return configuration.load();
    }

    private static PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder(
                "pbkdf2",
                Map.of("pbkdf2", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8())
        );
    }

    private Flyway migrateSystemCatalog(String schema) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .javaMigrations(
                        new R__SeedPermissionsAndRoles(),
                        new R__SynchronizeSystemGamLocations()
                )
                .load();
    }

    private List<Map<String, Object>> systemCatalogSnapshot(String schema) {
        return jdbcTemplate.queryForList(
                "SELECT id, code, system_managed, catalog_current, name, street, city, state, "
                        + "postal_code, country_code, latitude, longitude, identity_name, "
                        + "identity_street, identity_city, identity_state, identity_postal_code, "
                        + "identity_country_code, created_at, created_by, updated_at, updated_by, "
                        + "deleted_at, deleted_by FROM " + schema + ".gam_locations "
                        + "WHERE system_managed = TRUE ORDER BY code"
        );
    }

    private void forceSystemCatalogRerun(String schema, Flyway flyway) {
        int removedHistoryRows = jdbcTemplate.update(
                "DELETE FROM " + schema + ".flyway_schema_history "
                        + "WHERE version IS NULL AND description = 'SynchronizeSystemGamLocations'"
        );
        assertThat(removedHistoryRows).isEqualTo(1);
        flyway.migrate();
    }

    private UUID insertOrdinaryDbcaDuplicate(String schema) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.parse("2000-01-01T00:00:00Z"));
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".gam_locations ("
                        + "id, name, street, city, state, postal_code, country_code, "
                        + "identity_name, identity_street, identity_city, identity_state, "
                        + "identity_postal_code, identity_country_code, created_at, updated_at"
                        + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                "Dom Bosco Cidade Alta",
                "Rua Alfredo Guedes, 1199 - Bairro Alto",
                "Piracicaba",
                "SP",
                "13419-080",
                "BR",
                "dom bosco cidade alta",
                "rua alfredo guedes, 1199 - bairro alto",
                "piracicaba",
                "sp",
                "13419-080",
                "br",
                now,
                now
        );
        return id;
    }

    private UUID insertHistoricalEvent(String schema, UUID locationId) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.parse("2003-01-01T12:00:00Z"));
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".events ("
                        + "id, title, description, gam_location_id, required_permission_id, "
                        + "type, status, begin_date, end_date, created_at, updated_at"
                        + ") VALUES (?, ?, ?, ?, NULL, CAST(? AS " + schema + ".event_type_enum), "
                        + "CAST(? AS " + schema + ".event_status_enum), ?, ?, ?, ?)",
                id,
                "Historical Event for retired system location",
                "",
                locationId,
                "GENERIC",
                "COMPLETED",
                Timestamp.from(now.toInstant().minusSeconds(7_200)),
                Timestamp.from(now.toInstant().minusSeconds(3_600)),
                now,
                now
        );
        return id;
    }

    private List<Entry> catalogWithLifecycle(String code, Lifecycle lifecycle) {
        return SystemGamLocationCatalog.entries().stream()
                .map(entry -> entry.code().equals(code)
                        ? new Entry(
                                entry.code(),
                                lifecycle,
                                entry.name(),
                                entry.street(),
                                entry.city(),
                                entry.state(),
                                entry.postalCode(),
                                entry.countryCode(),
                                entry.latitude(),
                                entry.longitude()
                        )
                        : entry)
                .toList();
    }

    private MockedStatic<SystemGamLocationCatalog> mockCatalog(List<Entry> entries) {
        MockedStatic<SystemGamLocationCatalog> catalog =
                Mockito.mockStatic(SystemGamLocationCatalog.class);
        catalog.when(SystemGamLocationCatalog::entries).thenReturn(entries);
        catalog.when(() -> SystemGamLocationCatalog.find(Mockito.anyString()))
                .thenAnswer(invocation -> {
                    String code = invocation.getArgument(0);
                    return entries.stream()
                            .filter(entry -> entry.code().equals(code))
                            .findFirst();
                });
        catalog.when(() -> SystemGamLocationCatalog.findCurrent(Mockito.anyString()))
                .thenAnswer(invocation -> {
                    String code = invocation.getArgument(0);
                    Optional<Entry> entry = entries.stream()
                            .filter(candidate -> candidate.code().equals(code))
                            .findFirst();
                    return entry.filter(Entry::current);
                });
        return catalog;
    }

    private static DataSource dataSource() {
        return dataSource(null);
    }

    private static DataSource dataSource(String schema) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        String url = POSTGRES.getJdbcUrl();
        if (schema != null) {
            url += "&currentSchema=" + schema;
        }
        dataSource.setUrl(url);
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private static String uniqueSchema(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
