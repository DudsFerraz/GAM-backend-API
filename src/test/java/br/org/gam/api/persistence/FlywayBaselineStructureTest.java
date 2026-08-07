package br.org.gam.api.persistence;

import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@StructuralTest
@DisplayName("Structure - Pre-production Flyway baseline")
class FlywayBaselineStructureTest {

    private static final Path MIGRATION_DIRECTORY = Path.of(
            "src", "main", "resources", "db", "migration"
    );
    private static final Pattern VERSIONED_MIGRATION = Pattern.compile("^V(\\d+)__.+\\.sql$");
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?i)\\bCREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([a-z_][a-z0-9_]*)"
    );
    private static final Pattern CREATE_ENUM = Pattern.compile(
            "(?i)\\bCREATE\\s+TYPE\\s+([a-z_][a-z0-9_]*)\\s+AS\\s+ENUM\\b"
    );
    private static final Pattern COMPATIBILITY_TRANSITION = Pattern.compile(
            "(?i)\\bALTER\\s+TABLE\\b"
                    + "|\\bUPDATE\\s+[a-z_][a-z0-9_]*\\b"
                    + "|\\bRENAME\\s+(?:TO|COLUMN|CONSTRAINT)\\b"
                    + "|\\bDROP\\s+(?:COLUMN|CONSTRAINT)\\b"
    );
    private static final Map<Integer, MigrationContract> EXPECTED_BASELINE = expectedBaseline();

    @Test
    @DisplayName("ADR-0022 - versioned SQL directory -> exact consecutive V1-V25 manifest")
    void versionedSqlDirectoryShouldContainTheExactConsecutiveBaselineManifest() throws IOException {
        Map<Integer, String> actualManifest = new LinkedHashMap<>();
        for (Path migration : versionedMigrations()) {
            Matcher matcher = VERSIONED_MIGRATION.matcher(migration.getFileName().toString());
            assertThat(matcher.matches())
                    .as("versioned migration name %s", migration)
                    .isTrue();
            int version = Integer.parseInt(matcher.group(1));
            assertThat(actualManifest.put(version, migration.getFileName().toString()))
                    .as("duplicate Flyway version V%s", version)
                    .isNull();
        }

        Map<Integer, String> expectedManifest = new LinkedHashMap<>();
        EXPECTED_BASELINE.forEach((version, contract) -> expectedManifest.put(version, contract.filename()));

        assertThat(actualManifest).containsExactlyEntriesOf(expectedManifest);
        assertThat(actualManifest.keySet()).containsExactlyElementsOf(
                java.util.stream.IntStream.rangeClosed(1, 25).boxed().toList()
        );
    }

    @Test
    @DisplayName("ADR-0022 - each baseline migration -> owns its declared table and local enum mirrors")
    void eachBaselineMigrationShouldOwnItsDeclaredTableAndLocalEnumMirrors() throws IOException {
        for (Map.Entry<Integer, MigrationContract> entry : EXPECTED_BASELINE.entrySet()) {
            MigrationContract contract = entry.getValue();
            Path migration = MIGRATION_DIRECTORY.resolve(contract.filename());
            assertThat(migration)
                    .as("baseline migration V%s", entry.getKey())
                    .exists();

            String sql = Files.readString(migration);
            assertThat(matches(CREATE_TABLE, sql))
                    .as("tables created by %s", contract.filename())
                    .containsExactlyElementsOf(contract.tables());
            assertThat(matches(CREATE_ENUM, sql))
                    .as("enum mirrors created by %s", contract.filename())
                    .containsExactlyInAnyOrderElementsOf(contract.enums());
        }
    }

    @Test
    @DisplayName("ADR-0022 - current-state baseline SQL -> no compatibility-only transition operations")
    void currentStateBaselineShouldContainNoCompatibilityOnlyTransitionOperations() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path migration : versionedMigrations()) {
            String sql = Files.readString(migration);
            Matcher matcher = COMPATIBILITY_TRANSITION.matcher(sql);
            while (matcher.find()) {
                offenders.add(migration.getFileName() + ": " + matcher.group());
            }
        }

        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("ADR-0021 and ADR-0022 - production-safe SQL path -> Java repeatables and development callback stay separate")
    void nonVersionedMigrationResponsibilitiesShouldRemainOutsideTheVersionedSqlBaseline() throws IOException {
        try (Stream<Path> files = Files.list(MIGRATION_DIRECTORY)) {
            assertThat(files
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(filename -> filename.startsWith("R__") || filename.equals("afterMigrate.sql"))
                    .toList())
                    .isEmpty();
        }

        assertThat(Path.of(
                "src", "main", "java", "br", "org", "gam", "api", "db", "migration",
                "R__SeedPermissionsAndRoles.java"
        )).exists();
        assertThat(Path.of(
                "src", "main", "java", "br", "org", "gam", "api", "db", "migration",
                "R__SynchronizeSystemGamLocations.java"
        )).exists();
        assertThat(Path.of(
                "src", "main", "resources", "db", "dev-migration", "afterMigrate.sql"
        )).exists();
    }

    @Test
    @DisplayName("ADR-0022 - V6 -> directly creates final system GamLocation ownership schema")
    void v6ShouldDirectlyCreateTheFinalSystemGamLocationOwnershipSchema() throws IOException {
        String sql = Files.readString(MIGRATION_DIRECTORY.resolve(
                "V6__create_gam_locations_table.sql"
        ));

        assertThat(sql)
                .containsIgnoringCase("code VARCHAR(32)")
                .containsIgnoringCase("system_managed BOOLEAN NOT NULL DEFAULT FALSE")
                .containsIgnoringCase("catalog_current BOOLEAN NOT NULL DEFAULT FALSE")
                .contains("CONSTRAINT check_gam_locations_system_ownership")
                .contains("CHECK (system_managed = (code IS NOT NULL))")
                .contains("CONSTRAINT check_gam_locations_catalog_current")
                .contains("CHECK (NOT catalog_current OR system_managed)")
                .contains("CONSTRAINT check_gam_locations_code_format")
                .contains("CHECK (code IS NULL OR code ~ '^[A-Z][A-Z0-9_]*$')")
                .contains("CREATE UNIQUE INDEX idx_gam_locations_code")
                .contains("ON gam_locations (code)");
    }

    private static List<Path> versionedMigrations() throws IOException {
        try (Stream<Path> files = Files.list(MIGRATION_DIRECTORY)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> VERSIONED_MIGRATION.matcher(path.getFileName().toString()).matches())
                    .sorted((left, right) -> Integer.compare(version(left), version(right)))
                    .toList();
        }
    }

    private static int version(Path migration) {
        Matcher matcher = VERSIONED_MIGRATION.matcher(migration.getFileName().toString());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a versioned migration: " + migration);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static List<String> matches(Pattern pattern, String input) {
        List<String> matches = new ArrayList<>();
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            matches.add(matcher.group(1));
        }
        return matches;
    }

    private static Map<Integer, MigrationContract> expectedBaseline() {
        Map<Integer, MigrationContract> baseline = new LinkedHashMap<>();
        baseline.put(1, migration("V1__create_accounts_table.sql", "accounts"));
        baseline.put(2, migration("V2__create_activity_logs_table.sql", "activity_logs"));
        baseline.put(3, migration("V3__create_roles_table.sql", "roles"));
        baseline.put(4, migration("V4__create_permissions_table.sql", "permissions"));
        baseline.put(5, migration(
                "V5__create_members_table.sql",
                List.of(
                        "member_information_import_batches",
                        "members",
                        "member_experiences",
                        "member_sacraments",
                        "member_contribution_areas",
                        "member_other_contribution_areas",
                        "annual_member_information_responses",
                        "annual_member_occupations"
                ),
                "member_status_enum"
                , "member_information_status_enum"
                , "member_experience_type_enum"
                , "member_sacrament_type_enum"
                , "member_contribution_area_enum"
                , "member_occupation_enum"
                , "member_mass_attendance_frequency_enum"
                , "member_coordination_interest_enum"
        ));
        baseline.put(6, migration("V6__create_gam_locations_table.sql", "gam_locations"));
        baseline.put(7, migration(
                "V7__create_events_table.sql",
                "events",
                "event_status_enum",
                "event_type_enum"
        ));
        baseline.put(8, migration("V8__create_presences_table.sql", "presences"));
        baseline.put(9, migration("V9__create_oratorios_table.sql", "oratorios"));
        baseline.put(10, migration("V10__create_oratorianos_table.sql", "oratorianos"));
        baseline.put(11, migration("V11__create_missas_table.sql", "missas"));
        baseline.put(12, migration("V12__create_refresh_tokens_table.sql", "refresh_tokens"));
        baseline.put(13, migration(
                "V13__create_membership_solicitations_table.sql",
                "membership_solicitations",
                "membership_solicitation_status_enum"
        ));
        baseline.put(14, migration(
                "V14__create_oratoriano_attendances_table.sql",
                "oratoriano_attendances"
        ));
        baseline.put(15, migration(
                "V15__create_oratoriano_additional_forms_table.sql",
                "oratoriano_additional_forms",
                "oratoriano_form_status_enum",
                "oratoriano_form_origin_enum"
        ));
        baseline.put(16, migration(
                "V16__create_oratoriano_form_print_snapshots_table.sql",
                "oratoriano_form_print_snapshots",
                "oratoriano_form_print_mode_enum"
        ));
        baseline.put(17, migration(
                "V17__create_oratoriano_form_attachments_table.sql",
                "oratoriano_form_attachments"
        ));
        baseline.put(18, migration("V18__create_role_permissions_table.sql", "role_permissions"));
        baseline.put(19, migration("V19__create_account_roles_table.sql", "account_roles"));
        baseline.put(20, migration("V20__create_oratorio_lanche_table.sql", "oratorio_lanche"));
        baseline.put(21, migration(
                "V21__create_oratorio_bt_jovens_table.sql",
                "oratorio_bt_jovens"
        ));
        baseline.put(22, migration(
                "V22__create_oratorio_bt_criancas_table.sql",
                "oratorio_bt_criancas"
        ));
        baseline.put(23, migration(
                "V23__create_oratorio_presences_oratorianos_table.sql",
                "oratorio_presences_oratorianos"
        ));
        baseline.put(24, migration(
                "V24__create_missa_acolhida_members_table.sql",
                "missa_acolhida_members"
        ));
        baseline.put(25, migration(
                "V25__create_oratorio_team_assignments_table.sql",
                "oratorio_team_assignments",
                "oratorio_team_type_enum"
        ));
        return Collections.unmodifiableMap(baseline);
    }

    private static MigrationContract migration(String filename, String table, String... enums) {
        return migration(filename, List.of(table), enums);
    }

    private static MigrationContract migration(String filename, List<String> tables, String... enums) {
        return new MigrationContract(filename, List.copyOf(tables), List.of(enums));
    }

    private record MigrationContract(String filename, List<String> tables, List<String> enums) {
    }
}
