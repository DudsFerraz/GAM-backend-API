package br.org.gam.api.persistence;

import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.event.oratorio.application.OratorioApiModels.TeamType;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.solicitation.domain.MembershipSolicitationStatus;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.FormOrigin;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.FormStatus;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.PrintMode;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.rbac.role.domain.SystemRole;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.PersistenceTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@PersistenceTest
@DisplayName("Persistence - Pre-production Flyway baseline")
class FlywayBaselinePersistenceIT extends FlywayMigrationTestSupport {

    private static final String PRODUCTION_MIGRATION_PATH = "classpath:db/migration";
    private static final Path EXPECTED_SCHEMA_SNAPSHOT = Path.of(
            "src", "test", "resources", "db", "expected", "preproduction-baseline-schema.txt"
    );

    @Test
    @DisplayName("ADR-0022 - fresh PostgreSQL database -> exact accepted current schema contract")
    void freshDatabaseShouldCreateTheAcceptedCurrentSchemaContract() throws IOException {
        String schema = uniqueSchema("baseline_schema");
        migrate(schema, PRODUCTION_MIGRATION_PATH).migrate();

        String actual = schemaSnapshot(schema);
        assertThat(actual).isEqualTo(normalized(Files.readString(EXPECTED_SCHEMA_SNAPSHOT)));
    }

    @Test
    @DisplayName("REQ-DATA-010 and REQ-DATA-011 - accepted, application, and database enum catalogs -> exact mirror")
    void acceptedApplicationAndDatabaseEnumCatalogsShouldMatchExactly() {
        String schema = uniqueSchema("baseline_enums");
        migrate(schema, PRODUCTION_MIGRATION_PATH).migrate();

        Map<String, List<String>> databaseLabels = new LinkedHashMap<>();
        jdbcTemplate.queryForList(
                "SELECT type.typname AS enum_name, value.enumlabel AS enum_label "
                        + "FROM pg_type type "
                        + "JOIN pg_namespace namespace ON namespace.oid = type.typnamespace "
                        + "JOIN pg_enum value ON value.enumtypid = type.oid "
                        + "WHERE namespace.nspname = ? "
                        + "ORDER BY type.typname, value.enumsortorder",
                schema
        ).forEach(row -> databaseLabels
                .computeIfAbsent((String) row.get("enum_name"), ignored -> new ArrayList<>())
                .add((String) row.get("enum_label")));

        Map<String, List<String>> acceptedLabels = expectedEnumLabels();
        assertThat(applicationEnumLabels())
                .as("application enum catalogs must exactly match the accepted requirement catalogs")
                .isEqualTo(acceptedLabels);
        assertThat(databaseLabels)
                .as("database enum mirrors must exactly match the accepted requirement catalogs")
                .isEqualTo(acceptedLabels);
    }

    @Test
    @DisplayName("ADR-0021 and ADR-0022 - production migration lifecycle -> V1-V25 precede successful Java repeatables")
    void productionRepeatablesShouldRunSuccessfullyAfterTheCompleteVersionedBaseline() {
        String schema = uniqueSchema("baseline_repeatables");
        migrate(schema, PRODUCTION_MIGRATION_PATH).migrate();

        assertThat(jdbcTemplate.queryForList(
                "SELECT version FROM " + schema + ".flyway_schema_history "
                        + "WHERE version IS NOT NULL AND success "
                        + "ORDER BY installed_rank",
                String.class
        )).containsExactlyElementsOf(IntStream.rangeClosed(1, 25)
                .mapToObj(Integer::toString)
                .toList());

        assertThat(jdbcTemplate.queryForList(
                "SELECT description FROM " + schema + ".flyway_schema_history "
                        + "WHERE version IS NULL AND type <> 'SCHEMA' AND success "
                        + "ORDER BY description",
                String.class
        )).containsExactly(
                "SeedPermissionsAndRoles",
                "SynchronizeSystemGamLocations"
        );

        Integer lastVersionedRank = jdbcTemplate.queryForObject(
                "SELECT MAX(installed_rank) FROM " + schema + ".flyway_schema_history "
                        + "WHERE version IS NOT NULL AND success",
                Integer.class
        );
        Integer firstRepeatableRank = jdbcTemplate.queryForObject(
                "SELECT MIN(installed_rank) FROM " + schema + ".flyway_schema_history "
                        + "WHERE version IS NULL AND type <> 'SCHEMA' AND success",
                Integer.class
        );
        assertThat(firstRepeatableRank).isGreaterThan(lastVersionedRank);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".roles "
                        + "WHERE system_managed AND deleted_at IS NULL",
                Long.class
        )).isEqualTo((long) SystemRole.values().length);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".permissions "
                        + "WHERE system_managed AND deleted_at IS NULL",
                Long.class
        )).isEqualTo((long) PermissionEnum.values().length);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".gam_locations "
                        + "WHERE identity_name = 'dom bosco sao mario' AND deleted_at IS NULL",
                Long.class
        )).isEqualTo(1L);
    }

    @Test
    @DisplayName("REQ-DATA-007 and ADR-0022 - default migration path -> development fixtures are excluded")
    void defaultMigrationPathShouldExcludeDevelopmentFixtures() {
        String schema = uniqueSchema("baseline_production_path");
        migrate(schema, PRODUCTION_MIGRATION_PATH).migrate();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".accounts",
                Long.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".events",
                Long.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".activity_logs",
                Long.class
        )).isZero();
    }

    private String schemaSnapshot(String schema) {
        List<String> lines = new ArrayList<>();
        lines.add("[columns]");
        jdbcTemplate.queryForList(
                "SELECT table_definition.relname AS table_name, column_definition.attname AS column_name, "
                        + "format_type(column_definition.atttypid, column_definition.atttypmod) AS data_type, "
                        + "column_definition.attnotnull AS not_null, "
                        + "pg_get_expr(default_definition.adbin, default_definition.adrelid) AS default_expression "
                        + "FROM pg_class table_definition "
                        + "JOIN pg_namespace namespace ON namespace.oid = table_definition.relnamespace "
                        + "JOIN pg_attribute column_definition ON column_definition.attrelid = table_definition.oid "
                        + "LEFT JOIN pg_attrdef default_definition "
                        + "ON default_definition.adrelid = table_definition.oid "
                        + "AND default_definition.adnum = column_definition.attnum "
                        + "WHERE namespace.nspname = ? "
                        + "AND table_definition.relkind = 'r' "
                        + "AND table_definition.relname <> 'flyway_schema_history' "
                        + "AND column_definition.attnum > 0 "
                        + "AND NOT column_definition.attisdropped "
                        + "ORDER BY table_definition.relname, column_definition.attname",
                schema
        ).forEach(row -> lines.add(
                row.get("table_name") + "." + row.get("column_name")
                        + " | " + withoutSchema(row.get("data_type"), schema)
                        + " | " + ((Boolean) row.get("not_null") ? "NOT NULL" : "NULL")
                        + " | default=" + valueOrNone(row.get("default_expression"))
        ));

        lines.add("");
        lines.add("[constraints]");
        jdbcTemplate.queryForList(
                "SELECT table_definition.relname AS table_name, constraint_definition.conname AS constraint_name, "
                        + "pg_get_constraintdef(constraint_definition.oid, true) AS definition "
                        + "FROM pg_constraint constraint_definition "
                        + "JOIN pg_class table_definition "
                        + "ON table_definition.oid = constraint_definition.conrelid "
                        + "JOIN pg_namespace namespace ON namespace.oid = table_definition.relnamespace "
                        + "WHERE namespace.nspname = ? "
                        + "AND table_definition.relname <> 'flyway_schema_history' "
                        + "ORDER BY table_definition.relname, constraint_definition.conname",
                schema
        ).forEach(row -> lines.add(
                row.get("table_name") + "." + row.get("constraint_name")
                        + " | " + withoutSchema(row.get("definition"), schema)
        ));

        lines.add("");
        lines.add("[indexes]");
        jdbcTemplate.queryForList(
                "SELECT tablename AS table_name, indexname AS index_name, indexdef AS definition "
                        + "FROM pg_indexes "
                        + "WHERE schemaname = ? AND tablename <> 'flyway_schema_history' "
                        + "ORDER BY tablename, indexname",
                schema
        ).forEach(row -> lines.add(
                row.get("table_name") + "." + row.get("index_name")
                        + " | " + withoutSchema(row.get("definition"), schema)
        ));

        lines.add("");
        lines.add("[enums]");
        jdbcTemplate.queryForList(
                "SELECT type.typname AS enum_name, "
                        + "string_agg(value.enumlabel, ',' ORDER BY value.enumsortorder) AS labels "
                        + "FROM pg_type type "
                        + "JOIN pg_namespace namespace ON namespace.oid = type.typnamespace "
                        + "JOIN pg_enum value ON value.enumtypid = type.oid "
                        + "WHERE namespace.nspname = ? "
                        + "GROUP BY type.typname "
                        + "ORDER BY type.typname",
                schema
        ).forEach(row -> lines.add(row.get("enum_name") + " | " + row.get("labels")));

        return String.join("\n", lines) + "\n";
    }

    private static Map<String, List<String>> expectedEnumLabels() {
        Map<String, List<String>> labels = new LinkedHashMap<>();
        labels.put("event_status_enum", List.of(
                "SCHEDULED", "COMPLETED", "LOCKED", "FINALIZED", "CANCELLED"
        ));
        labels.put("event_type_enum", List.of("GENERIC", "ORATORIO", "MISSA"));
        labels.put("member_status_enum", List.of("ACTIVE", "INACTIVE"));
        labels.put("membership_solicitation_status_enum", List.of(
                "PENDING", "APPROVED", "REJECTED"
        ));
        labels.put("oratoriano_form_origin_enum", List.of(
                "PAPER_TRANSCRIPTION", "DIRECT_SYSTEM_ENTRY"
        ));
        labels.put("oratoriano_form_print_mode_enum", List.of(
                "IDENTIFIED_BLANK", "PREFILLED"
        ));
        labels.put("oratoriano_form_status_enum", List.of(
                "DRAFT", "COMPLETED", "SUPERSEDED", "REVOKED"
        ));
        labels.put("oratorio_team_type_enum", List.of(
                "LANCHE", "GINCANA", "BOA_TARDE_CRIANCAS", "BOA_TARDE_JOVENS"
        ));
        return labels;
    }

    private static Map<String, List<String>> applicationEnumLabels() {
        Map<String, List<String>> labels = new LinkedHashMap<>();
        labels.put("event_status_enum", enumNames(EventStatus.values()));
        labels.put("event_type_enum", enumNames(EventType.values()));
        labels.put("member_status_enum", enumNames(MemberStatus.values()));
        labels.put("membership_solicitation_status_enum", enumNames(MembershipSolicitationStatus.values()));
        labels.put("oratoriano_form_origin_enum", enumNames(FormOrigin.values()));
        labels.put("oratoriano_form_print_mode_enum", enumNames(PrintMode.values()));
        labels.put("oratoriano_form_status_enum", enumNames(FormStatus.values()));
        labels.put("oratorio_team_type_enum", enumNames(TeamType.values()));
        return labels;
    }

    private static List<String> enumNames(Enum<?>... values) {
        return java.util.Arrays.stream(values)
                .map(Enum::name)
                .toList();
    }

    private static String valueOrNone(Object value) {
        return value == null ? "<none>" : value.toString();
    }

    private static String withoutSchema(Object value, String schema) {
        return value.toString()
                .replace("\"" + schema + "\".", "")
                .replace(schema + ".", "");
    }

    private static String normalized(String value) {
        return value.replace("\r\n", "\n").stripTrailing() + "\n";
    }
}
