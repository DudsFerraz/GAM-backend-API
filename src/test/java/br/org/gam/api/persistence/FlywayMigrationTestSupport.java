package br.org.gam.api.persistence;

import br.org.gam.api.db.migration.R__SeedPermissionsAndRoles;
import br.org.gam.api.db.migration.R__SynchronizeSystemGamLocations;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

abstract class FlywayMigrationTestSupport {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine")
    );

    static {
        POSTGRES.start();
    }

    protected final DataSource dataSource = dataSource();
    protected final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

    protected Flyway migrate(String schema, String... locations) {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations(locations)
                .javaMigrations(
                        new R__SeedPermissionsAndRoles(),
                        new R__SynchronizeSystemGamLocations()
                );
        return configuration.load();
    }

    protected static String uniqueSchema(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }
}
