package br.org.gam.api.persistence;

import br.org.gam.api.db.reference.SystemReferenceDataValidator;
import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.event.oratorio.application.OratorioApiModels.TeamType;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.solicitation.domain.MembershipSolicitationStatus;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.FormOrigin;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.FormStatus;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.PrintMode;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.PersistenceTest;
import br.org.gam.api.testing.integration.PostgreSQLIntegrationTest;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@FunctionalTest
@IntegrationTest
@PersistenceTest
@DisplayName("Persistence - Database Enum Mirrors")
class DatabaseEnumMirrorPersistenceIT extends PostgreSQLIntegrationTest {
    private static final String FALSE_DRIFT_SCHEMA = "enum_false_drift_test";
    private static final String EFFECTIVE_SCHEMA = "enum_effective_schema_test";

    /*
     * These literals are the independent accepted-catalog oracle for REQ-DATA-010 and REQ-DATA-011.
     * They intentionally are not derived from either Java enum constants or PostgreSQL labels.
     */
    private static final Map<String, Set<String>> ACCEPTED_CATALOGS = Map.of(
            "member_status_enum", Set.of("ACTIVE", "INACTIVE"),
            "event_type_enum", Set.of("GENERIC", "ORATORIO", "MISSA"),
            "event_status_enum", Set.of("SCHEDULED", "COMPLETED", "LOCKED", "FINALIZED", "CANCELLED"),
            "membership_solicitation_status_enum", Set.of("PENDING", "APPROVED", "REJECTED"),
            "oratorio_team_type_enum", Set.of(
                    "LANCHE", "GINCANA", "BOA_TARDE_CRIANCAS", "BOA_TARDE_JOVENS"),
            "oratoriano_form_status_enum", Set.of("DRAFT", "COMPLETED", "SUPERSEDED", "REVOKED"),
            "oratoriano_form_origin_enum", Set.of("PAPER_TRANSCRIPTION", "DIRECT_SYSTEM_ENTRY"),
            "oratoriano_form_print_mode_enum", Set.of("IDENTIFIED_BLANK", "PREFILLED")
    );

    private static final Map<String, Supplier<? extends Enum<?>[]>> APPLICATION_CATALOGS = Map.of(
            "member_status_enum", MemberStatus::values,
            "event_type_enum", EventType::values,
            "event_status_enum", EventStatus::values,
            "membership_solicitation_status_enum", MembershipSolicitationStatus::values,
            "oratorio_team_type_enum", TeamType::values,
            "oratoriano_form_status_enum", FormStatus::values,
            "oratoriano_form_origin_enum", FormOrigin::values,
            "oratoriano_form_print_mode_enum", PrintMode::values
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("REQ-DATA-010 and REQ-DATA-011 - accepted, application, and PostgreSQL catalogs match exactly")
    void acceptedApplicationAndDatabaseCatalogsShouldMatchExactly() {
        assertThat(APPLICATION_CATALOGS).containsOnlyKeys(ACCEPTED_CATALOGS.keySet());

        ACCEPTED_CATALOGS.forEach((databaseType, acceptedValues) -> {
            assertThat(applicationValues(databaseType))
                    .as("%s application catalog", databaseType)
                    .containsExactlyInAnyOrderElementsOf(acceptedValues);
            assertThat(databaseValues(databaseType))
                    .as("%s PostgreSQL enum mirror", databaseType)
                    .containsExactlyInAnyOrderElementsOf(acceptedValues);
        });
    }

    @Test
    @DisplayName("REQ-DATA-011 - same-named enum in an unrelated schema does not create false drift")
    void unrelatedSchemaEnumShouldNotCreateFalseDrift() {
        try {
            jdbcTemplate.execute("CREATE SCHEMA " + FALSE_DRIFT_SCHEMA);
            createEnum(
                    jdbcTemplate,
                    FALSE_DRIFT_SCHEMA,
                    "member_status_enum",
                    Set.of("ACTIVE", "INACTIVE", "UNRELATED_ONLY")
            );

            assertThatCode(() -> new SystemReferenceDataValidator(jdbcTemplate).afterPropertiesSet())
                    .doesNotThrowAnyException();
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + FALSE_DRIFT_SCHEMA + " CASCADE");
        }
    }

    @Test
    @DisplayName("REQ-DATA-011 - same-named enums in other schemas cannot mask effective-schema drift")
    void otherSchemaEnumsShouldNotMaskEffectiveSchemaDrift() throws Exception {
        try {
            jdbcTemplate.execute("CREATE SCHEMA " + EFFECTIVE_SCHEMA);
            ACCEPTED_CATALOGS.forEach((databaseType, acceptedValues) -> createEnum(
                    jdbcTemplate,
                    EFFECTIVE_SCHEMA,
                    databaseType,
                    databaseType.equals("member_status_enum") ? Set.of("ACTIVE") : acceptedValues
            ));

            try (Connection connection = dataSource.getConnection()) {
                JdbcTemplate effectiveSchemaJdbcTemplate =
                        new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                effectiveSchemaJdbcTemplate.execute(
                        "SET search_path TO " + EFFECTIVE_SCHEMA + ", public"
                );

                assertThatThrownBy(
                        () -> new SystemReferenceDataValidator(effectiveSchemaJdbcTemplate)
                                .afterPropertiesSet()
                )
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("member_status_enum");
            }
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + EFFECTIVE_SCHEMA + " CASCADE");
        }
    }

    private Set<String> applicationValues(String databaseType) {
        return Arrays.stream(APPLICATION_CATALOGS.get(databaseType).get())
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> databaseValues(String databaseType) {
        return Set.copyOf(jdbcTemplate.query(
                "SELECT enum_label.enumlabel "
                        + "FROM pg_type enum_type "
                        + "JOIN pg_enum enum_label ON enum_label.enumtypid = enum_type.oid "
                        + "WHERE enum_type.typname = ?",
                (resultSet, rowNumber) -> resultSet.getString("enumlabel"),
                databaseType
        ));
    }

    private void createEnum(
            JdbcTemplate targetJdbcTemplate,
            String schema,
            String databaseType,
            Set<String> values
    ) {
        String labels = values.stream()
                .sorted()
                .map(value -> "'" + value + "'")
                .collect(Collectors.joining(", "));
        targetJdbcTemplate.execute(
                "CREATE TYPE " + schema + "." + databaseType + " AS ENUM (" + labels + ")"
        );
    }
}
