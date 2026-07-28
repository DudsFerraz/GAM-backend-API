package br.org.gam.api.gamLocation.application;

import br.org.gam.api.gamLocation.application.SystemGamLocationCatalog.Entry;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ValidateSystemGamLocationCatalog implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;
    private final String configuredOratorioLocationCode;
    private volatile UUID oratorioLocationId;

    public ValidateSystemGamLocationCatalog(
            JdbcTemplate jdbcTemplate,
            @Value("${gam.oratorio.location-code}") String configuredOratorioLocationCode
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.configuredOratorioLocationCode = configuredOratorioLocationCode;
    }

    @Override
    public void run(ApplicationArguments args) {
        Entry configuredEntry = SystemGamLocationCatalog.findCurrent(configuredOratorioLocationCode)
                .orElseThrow(() -> invalid(
                        "gam.oratorio.location-code must be one exact current system location code."
                ));

        failForUnknownSystemLocations();
        UUID configuredId = null;
        for (Entry entry : SystemGamLocationCatalog.entries()) {
            List<PersistedLocation> matches = findByCode(entry.code());
            if (matches.size() > 1) {
                throw invalid("More than one row uses reserved code " + entry.code() + ".");
            }
            if (matches.isEmpty()) {
                if (entry.current()) {
                    throw invalid("Current system location " + entry.code() + " is missing.");
                }
                assertReservedIdentity(entry, null);
                continue;
            }

            PersistedLocation persisted = matches.getFirst();
            if (!persisted.systemManaged()) {
                throw invalid("Reserved code " + entry.code() + " is not system-managed.");
            }
            if (persisted.catalogCurrent() != entry.current()) {
                throw invalid("Catalog lifecycle drifted for " + entry.code() + ".");
            }
            assertAcceptedMetadata(entry, persisted);
            assertReservedIdentity(entry, persisted.id());

            if (persisted.deletedAt() != null) {
                String lifecycle = entry.current() ? "Current" : "Retired";
                throw invalid(
                        lifecycle + " system location " + entry.code() + " is soft-deleted."
                );
            }
            if (entry.code().equals(configuredEntry.code())) {
                configuredId = persisted.id();
            }
        }

        if (configuredId == null) {
            throw invalid(
                    "Configured Oratorio system location "
                            + configuredOratorioLocationCode
                            + " is unavailable."
            );
        }
        oratorioLocationId = configuredId;
    }

    public UUID oratorioLocationId() {
        UUID resolved = oratorioLocationId;
        if (resolved == null) {
            throw new IllegalStateException("System GamLocation catalog validation has not completed.");
        }
        return resolved;
    }

    public String oratorioLocationCode() {
        return configuredOratorioLocationCode;
    }

    private void failForUnknownSystemLocations() {
        List<String> systemCodes = jdbcTemplate.query(
                "SELECT code FROM gam_locations WHERE system_managed = TRUE",
                (result, rowNumber) -> result.getString("code")
        );
        for (String code : systemCodes) {
            if (SystemGamLocationCatalog.find(code).isEmpty()) {
                throw invalid("Unknown system-managed location code " + code + ".");
            }
        }
    }

    private List<PersistedLocation> findByCode(String code) {
        return jdbcTemplate.query(
                selectColumns() + " WHERE code = ?",
                (result, rowNumber) -> persistedLocation(result),
                code
        );
    }

    private void assertReservedIdentity(Entry entry, UUID expectedId) {
        GamLocationNormalizer.Values values = entry.normalizedValues();
        List<UUID> identityMatches = jdbcTemplate.query(
                "SELECT id FROM gam_locations"
                        + " WHERE identity_name = ?"
                        + " AND identity_street = ?"
                        + " AND identity_city = ?"
                        + " AND identity_state = ?"
                        + " AND identity_postal_code = ?"
                        + " AND identity_country_code = ?",
                (result, rowNumber) -> result.getObject("id", UUID.class),
                values.identityName(),
                values.identityStreet(),
                values.identityCity(),
                values.identityState(),
                values.identityPostalCode(),
                values.identityCountryCode()
        );
        if (expectedId == null) {
            if (!identityMatches.isEmpty()) {
                throw invalid(
                        "Retired system location identity " + entry.code()
                                + " belongs to another row."
                );
            }
            return;
        }
        if (identityMatches.size() != 1 || !identityMatches.getFirst().equals(expectedId)) {
            throw invalid(
                    "System location duplicate identity is inconsistent for " + entry.code() + "."
            );
        }
    }

    private void assertAcceptedMetadata(Entry entry, PersistedLocation persisted) {
        GamLocationNormalizer.Values accepted = entry.normalizedValues();
        boolean matches = Objects.equals(persisted.name(), accepted.name())
                && Objects.equals(persisted.street(), accepted.street())
                && Objects.equals(persisted.city(), accepted.city())
                && Objects.equals(persisted.state(), accepted.state())
                && Objects.equals(persisted.postalCode(), accepted.postalCode())
                && Objects.equals(persisted.countryCode(), accepted.countryCode())
                && sameDecimal(persisted.latitude(), accepted.latitude())
                && sameDecimal(persisted.longitude(), accepted.longitude())
                && Objects.equals(persisted.identityName(), accepted.identityName())
                && Objects.equals(persisted.identityStreet(), accepted.identityStreet())
                && Objects.equals(persisted.identityCity(), accepted.identityCity())
                && Objects.equals(persisted.identityState(), accepted.identityState())
                && Objects.equals(
                        persisted.identityPostalCode(),
                        accepted.identityPostalCode()
                )
                && Objects.equals(
                        persisted.identityCountryCode(),
                        accepted.identityCountryCode()
                );
        if (!matches) {
            throw invalid("Application-owned metadata drifted for " + entry.code() + ".");
        }
    }

    private boolean sameDecimal(BigDecimal persisted, BigDecimal accepted) {
        if (persisted == null || accepted == null) {
            return persisted == accepted;
        }
        return persisted.compareTo(accepted) == 0;
    }

    private PersistedLocation persistedLocation(ResultSet result) throws SQLException {
        java.sql.Timestamp deletedAt = result.getTimestamp("deleted_at");
        return new PersistedLocation(
                result.getObject("id", UUID.class),
                result.getString("code"),
                result.getBoolean("system_managed"),
                result.getBoolean("catalog_current"),
                result.getString("name"),
                result.getString("street"),
                result.getString("city"),
                result.getString("state"),
                result.getString("postal_code"),
                result.getString("country_code"),
                result.getBigDecimal("latitude"),
                result.getBigDecimal("longitude"),
                result.getString("identity_name"),
                result.getString("identity_street"),
                result.getString("identity_city"),
                result.getString("identity_state"),
                result.getString("identity_postal_code"),
                result.getString("identity_country_code"),
                deletedAt == null ? null : deletedAt.toInstant()
        );
    }

    private String selectColumns() {
        return "SELECT id, code, system_managed, catalog_current, name, street, city, state, postal_code, "
                + "country_code, latitude, longitude, identity_name, identity_street, "
                + "identity_city, identity_state, identity_postal_code, identity_country_code, "
                + "deleted_at FROM gam_locations";
    }

    private IllegalStateException invalid(String message) {
        return new IllegalStateException(
                "System GamLocation catalog validation failed: " + message
        );
    }

    private record PersistedLocation(
            UUID id,
            String code,
            boolean systemManaged,
            boolean catalogCurrent,
            String name,
            String street,
            String city,
            String state,
            String postalCode,
            String countryCode,
            BigDecimal latitude,
            BigDecimal longitude,
            String identityName,
            String identityStreet,
            String identityCity,
            String identityState,
            String identityPostalCode,
            String identityCountryCode,
            Instant deletedAt
    ) {
    }
}
