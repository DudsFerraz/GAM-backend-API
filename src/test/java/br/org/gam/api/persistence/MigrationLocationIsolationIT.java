package br.org.gam.api.persistence;

import br.org.gam.api.GamApiApplication;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.PersistenceTest;
import br.org.gam.api.testing.integration.PostgreSQLIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@FunctionalTest
@IntegrationTest
@PersistenceTest
@DisplayName("Persistence - Migration Location Isolation")
class MigrationLocationIsolationIT extends PostgreSQLIntegrationTest {

    private static final String ISOLATED_SCHEMA = "production_fixture_rejection_test";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "classpath:db/dev-migration",
            "classpath:db/test-fixtures/reference-isolation"
    })
    @DisplayName("REQ-DATA-007 - production runtime rejects non-production fixture locations before fixtures run")
    void productionRuntimeShouldRejectFixtureLocation(String fixtureLocation) {
        assertProductionRejectsFixtureLocation(
                "--spring.flyway.locations=classpath:db/migration," + fixtureLocation
        );
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "classpath:db/dev-migration",
            "classpath:db/test-fixtures/reference-isolation"
    })
    @DisplayName("REQ-DATA-007 - indexed Flyway locations reject non-production fixtures before execution")
    void indexedFlywayLocationsShouldRejectFixtureLocation(String fixtureLocation) {
        assertProductionRejectsFixtureLocation(
                "--spring.flyway.locations[0]=classpath:db/migration",
                "--spring.flyway.locations[1]=" + fixtureLocation
        );
    }

    private void assertProductionRejectsFixtureLocation(String... locationArguments) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        ConfigurableApplicationContext applicationContext = null;
        Throwable startupFailure = null;

        try {
            SpringApplication application = new SpringApplication(GamApiApplication.class);
            List<String> arguments = new ArrayList<>(List.of(
                    "--server.port=0",
                    "--spring.datasource.url=" + isolatedSchemaUrl(),
                    "--spring.datasource.username=" + requiredProperty("spring.datasource.username"),
                    "--spring.datasource.password=" + requiredProperty("spring.datasource.password"),
                    "--spring.flyway.default-schema=" + ISOLATED_SCHEMA,
                    "--spring.flyway.schemas=" + ISOLATED_SCHEMA,
                    "--spring.jpa.properties.hibernate.default_schema=" + ISOLATED_SCHEMA,
                    "--jwt.secret-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                    "--GAM_PUBLIC_ORIGIN=https://test.example"
            ));
            arguments.addAll(List.of(locationArguments));
            try {
                applicationContext = application.run(arguments.toArray(String[]::new));
            } catch (Throwable failure) {
                startupFailure = failure;
            }

            assertThat(startupFailure)
                    .as("a production runtime must fail when a non-production fixture location is configured")
                    .isNotNull();
            assertThat(fixtureAccountCount(jdbcTemplate))
                    .as("fixture identities must not be applied before the production runtime refuses startup")
                    .isZero();
            assertThat(tableExists(jdbcTemplate, "reference_isolation_fixture_marker"))
                    .as("test fixture callbacks must not run before the production runtime refuses startup")
                    .isFalse();
        } finally {
            if (applicationContext != null) {
                applicationContext.close();
            }
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + ISOLATED_SCHEMA + " CASCADE");
        }
    }

    private long fixtureAccountCount(JdbcTemplate jdbcTemplate) {
        if (!tableExists(jdbcTemplate, "accounts")) {
            return 0;
        }
        return Objects.requireNonNull(
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM " + ISOLATED_SCHEMA + ".accounts",
                        Long.class
                ),
                "Expected fixture account count"
        );
    }

    private boolean tableExists(JdbcTemplate jdbcTemplate, String table) {
        String resolvedTable = jdbcTemplate.queryForObject(
                "SELECT to_regclass(?)",
                String.class,
                ISOLATED_SCHEMA + "." + table
        );
        return resolvedTable != null;
    }

    private String isolatedSchemaUrl() {
        String datasourceUrl = requiredProperty("spring.datasource.url");
        String separator = datasourceUrl.contains("?") ? "&" : "?";
        return datasourceUrl + separator + "currentSchema=" + ISOLATED_SCHEMA;
    }

    private String requiredProperty(String name) {
        return Objects.requireNonNull(environment.getProperty(name), "Missing test property " + name);
    }
}
