package br.org.gam.api.db.reference;

import java.util.Locale;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class MigrationLocationIsolation implements FlywayConfigurationCustomizer {
    private static final String DEVELOPMENT_FIXTURE_LOCATION = "db/dev-migration";
    private static final String TEST_FIXTURE_LOCATION = "db/test-fixtures";

    private final Environment environment;
    private final FlywayProperties flywayProperties;

    public MigrationLocationIsolation(Environment environment, FlywayProperties flywayProperties) {
        this.environment = environment;
        this.flywayProperties = flywayProperties;
    }

    @Override
    public void customize(FluentConfiguration configuration) {
        for (String location : flywayProperties.getLocations()) {
            validateLocation(location);
        }
    }

    private void validateLocation(String location) {
        String normalizedLocation = location.strip().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalizedLocation.contains(DEVELOPMENT_FIXTURE_LOCATION)
                && !environment.acceptsProfiles("dev")) {
            throw new IllegalStateException(
                    "Development Flyway fixture locations require the dev profile: " + location.strip()
            );
        }
        if (normalizedLocation.contains(TEST_FIXTURE_LOCATION)
                && !environment.acceptsProfiles("test")) {
            throw new IllegalStateException(
                    "Test Flyway fixture locations require the test profile: " + location.strip()
            );
        }
    }
}
