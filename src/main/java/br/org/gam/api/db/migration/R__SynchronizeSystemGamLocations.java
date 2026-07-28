package br.org.gam.api.db.migration;

import br.org.gam.api.gamLocation.application.GamLocationNormalizer;
import br.org.gam.api.gamLocation.application.SystemGamLocationCatalog;
import br.org.gam.api.gamLocation.application.SystemGamLocationCatalog.Entry;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class R__SynchronizeSystemGamLocations extends BaseJavaMigration {

    @Override
    public Integer getChecksum() {
        CRC32 checksum = new CRC32();
        for (Entry entry : SystemGamLocationCatalog.entries()) {
            GamLocationNormalizer.Values values = entry.normalizedValues();
            updateChecksum(checksum, entry.code());
            updateChecksum(checksum, entry.lifecycle().name());
            updateChecksum(checksum, values.name());
            updateChecksum(checksum, values.street());
            updateChecksum(checksum, values.city());
            updateChecksum(checksum, values.state());
            updateChecksum(checksum, values.postalCode());
            updateChecksum(checksum, values.countryCode());
            updateChecksum(checksum, coordinate(values.latitude()));
            updateChecksum(checksum, coordinate(values.longitude()));
            updateChecksum(checksum, values.identityName());
            updateChecksum(checksum, values.identityStreet());
            updateChecksum(checksum, values.identityCity());
            updateChecksum(checksum, values.identityState());
            updateChecksum(checksum, values.identityPostalCode());
            updateChecksum(checksum, values.identityCountryCode());
        }
        return (int) checksum.getValue();
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        Map<String, PersistedLocation> locationsByCode = preflight(connection);
        Timestamp now = Timestamp.from(Instant.now());

        for (Entry entry : SystemGamLocationCatalog.entries()) {
            PersistedLocation persisted = locationsByCode.get(entry.code());
            if (entry.current() && persisted == null) {
                insert(connection, entry, now);
            } else if (entry.current()) {
                reconcile(connection, persisted, entry, now);
            } else if (persisted != null) {
                retire(connection, persisted, now);
            }
        }
    }

    private Map<String, PersistedLocation> preflight(Connection connection) throws Exception {
        failForUnknownSystemCodes(connection);
        Map<String, PersistedLocation> locationsByCode = new HashMap<>();

        for (Entry entry : SystemGamLocationCatalog.entries()) {
            List<PersistedLocation> codeMatches = findByCode(connection, entry.code());
            if (codeMatches.size() > 1) {
                throw collision("More than one GamLocation uses reserved code " + entry.code() + ".");
            }

            PersistedLocation codeMatch = codeMatches.isEmpty() ? null : codeMatches.getFirst();
            if (codeMatch != null && !codeMatch.systemManaged()) {
                throw collision(
                        "Reserved code " + entry.code() + " belongs to a user-managed GamLocation."
                );
            }
            if (codeMatch != null) {
                locationsByCode.put(entry.code(), codeMatch);
            }

            GamLocationNormalizer.Values values = entry.normalizedValues();
            for (PersistedLocation identityMatch : findByIdentity(connection, values)) {
                if (codeMatch == null || !identityMatch.id().equals(codeMatch.id())) {
                    throw collision(
                            "Reserved duplicate identity for " + entry.code()
                                    + " belongs to another GamLocation."
                    );
                }
            }
        }
        return locationsByCode;
    }

    private void failForUnknownSystemCodes(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT code FROM gam_locations WHERE system_managed = TRUE"
        ); ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                String code = result.getString("code");
                if (SystemGamLocationCatalog.find(code).isEmpty()) {
                    throw collision("Unknown system-managed GamLocation code " + code + ".");
                }
            }
        }
    }

    private List<PersistedLocation> findByCode(Connection connection, String code) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                selectColumns() + " WHERE code = ?"
        )) {
            statement.setString(1, code);
            return query(statement);
        }
    }

    private List<PersistedLocation> findByIdentity(
            Connection connection,
            GamLocationNormalizer.Values values
    ) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                selectColumns()
                        + " WHERE identity_name = ?"
                        + " AND identity_street = ?"
                        + " AND identity_city = ?"
                        + " AND identity_state = ?"
                        + " AND identity_postal_code = ?"
                        + " AND identity_country_code = ?"
        )) {
            statement.setString(1, values.identityName());
            statement.setString(2, values.identityStreet());
            statement.setString(3, values.identityCity());
            statement.setString(4, values.identityState());
            statement.setString(5, values.identityPostalCode());
            statement.setString(6, values.identityCountryCode());
            return query(statement);
        }
    }

    private List<PersistedLocation> query(PreparedStatement statement) throws Exception {
        List<PersistedLocation> locations = new ArrayList<>();
        try (ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                locations.add(new PersistedLocation(
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
                        result.getTimestamp("deleted_at") == null
                                ? null
                                : result.getTimestamp("deleted_at").toInstant()
                ));
            }
        }
        return locations;
    }

    private String selectColumns() {
        return "SELECT id, code, system_managed, catalog_current, name, street, city, state, postal_code, "
                + "country_code, latitude, longitude, identity_name, identity_street, "
                + "identity_city, identity_state, identity_postal_code, identity_country_code, "
                + "deleted_at FROM gam_locations";
    }

    private void insert(Connection connection, Entry entry, Timestamp now) throws Exception {
        GamLocationNormalizer.Values values = entry.normalizedValues();
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO gam_locations ("
                        + "id, code, system_managed, catalog_current, name, street, city, state, postal_code, "
                        + "country_code, latitude, longitude, "
                        + "identity_name, identity_street, identity_city, identity_state, "
                        + "identity_postal_code, identity_country_code, created_at, updated_at"
                        + ") VALUES (?, ?, TRUE, TRUE, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            insert.setObject(1, UUIDGenerator.generateUUIDV7());
            insert.setString(2, entry.code());
            insert.setString(3, values.name());
            insert.setString(4, values.street());
            insert.setString(5, values.city());
            insert.setString(6, values.state());
            insert.setString(7, values.postalCode());
            insert.setString(8, values.countryCode());
            insert.setBigDecimal(9, values.latitude());
            insert.setBigDecimal(10, values.longitude());
            insert.setString(11, values.identityName());
            insert.setString(12, values.identityStreet());
            insert.setString(13, values.identityCity());
            insert.setString(14, values.identityState());
            insert.setString(15, values.identityPostalCode());
            insert.setString(16, values.identityCountryCode());
            insert.setTimestamp(17, now);
            insert.setTimestamp(18, now);
            insert.executeUpdate();
        }
    }

    private void reconcile(
            Connection connection,
            PersistedLocation persisted,
            Entry entry,
            Timestamp now
    ) throws Exception {
        GamLocationNormalizer.Values accepted = entry.normalizedValues();
        List<String> assignments = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();

        addChanged(
                assignments,
                parameters,
                "catalog_current",
                persisted.catalogCurrent(),
                true
        );
        addChanged(assignments, parameters, "name", persisted.name(), accepted.name());
        addChanged(assignments, parameters, "street", persisted.street(), accepted.street());
        addChanged(assignments, parameters, "city", persisted.city(), accepted.city());
        addChanged(assignments, parameters, "state", persisted.state(), accepted.state());
        addChanged(
                assignments,
                parameters,
                "postal_code",
                persisted.postalCode(),
                accepted.postalCode()
        );
        addChanged(
                assignments,
                parameters,
                "country_code",
                persisted.countryCode(),
                accepted.countryCode()
        );
        addChanged(
                assignments,
                parameters,
                "latitude",
                persisted.latitude(),
                accepted.latitude()
        );
        addChanged(
                assignments,
                parameters,
                "longitude",
                persisted.longitude(),
                accepted.longitude()
        );
        addChanged(
                assignments,
                parameters,
                "identity_name",
                persisted.identityName(),
                accepted.identityName()
        );
        addChanged(
                assignments,
                parameters,
                "identity_street",
                persisted.identityStreet(),
                accepted.identityStreet()
        );
        addChanged(
                assignments,
                parameters,
                "identity_city",
                persisted.identityCity(),
                accepted.identityCity()
        );
        addChanged(
                assignments,
                parameters,
                "identity_state",
                persisted.identityState(),
                accepted.identityState()
        );
        addChanged(
                assignments,
                parameters,
                "identity_postal_code",
                persisted.identityPostalCode(),
                accepted.identityPostalCode()
        );
        addChanged(
                assignments,
                parameters,
                "identity_country_code",
                persisted.identityCountryCode(),
                accepted.identityCountryCode()
        );

        if (persisted.deletedAt() != null) {
            assignments.add("deleted_at = NULL");
            assignments.add("deleted_by = NULL");
        }
        if (assignments.isEmpty()) {
            return;
        }

        assignments.add("updated_at = ?");
        parameters.add(now);
        assignments.add("updated_by = NULL");

        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE gam_locations SET " + String.join(", ", assignments) + " WHERE id = ?"
        )) {
            int index = 1;
            for (Object parameter : parameters) {
                update.setObject(index++, parameter);
            }
            update.setObject(index, persisted.id());
            update.executeUpdate();
        }
    }

    private void retire(
            Connection connection,
            PersistedLocation persisted,
            Timestamp now
    ) throws Exception {
        List<String> assignments = new ArrayList<>();
        if (persisted.catalogCurrent()) {
            assignments.add("catalog_current = FALSE");
        }
        if (persisted.deletedAt() != null) {
            assignments.add("deleted_at = NULL");
            assignments.add("deleted_by = NULL");
        }
        if (assignments.isEmpty()) {
            return;
        }
        assignments.add("updated_at = ?");
        assignments.add("updated_by = NULL");

        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE gam_locations SET " + String.join(", ", assignments) + " WHERE id = ?"
        )) {
            update.setTimestamp(1, now);
            update.setObject(2, persisted.id());
            update.executeUpdate();
        }
    }

    private void addChanged(
            List<String> assignments,
            List<Object> parameters,
            String column,
            Object persisted,
            Object accepted
    ) {
        if (!sameValue(persisted, accepted)) {
            assignments.add(column + " = ?");
            parameters.add(accepted);
        }
    }

    private boolean sameValue(Object persisted, Object accepted) {
        if (persisted instanceof BigDecimal persistedDecimal
                && accepted instanceof BigDecimal acceptedDecimal) {
            return persistedDecimal.compareTo(acceptedDecimal) == 0;
        }
        return Objects.equals(persisted, accepted);
    }

    private IllegalStateException collision(String message) {
        return new IllegalStateException("System GamLocation catalog collision: " + message);
    }

    private static String coordinate(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static void updateChecksum(CRC32 checksum, String value) {
        byte[] encoded = Objects.toString(value, "<null>").getBytes(StandardCharsets.UTF_8);
        checksum.update(encoded);
        checksum.update(0);
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
