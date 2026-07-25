package br.org.gam.api.db.migration;

import br.org.gam.api.shared.persistence.UUIDGenerator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class R__SeedOratorioGamLocation extends BaseJavaMigration {
    private static final String IDENTITY_NAME = "sao mario";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (PreparedStatement lookup = connection.prepareStatement(
                "SELECT 1 FROM gam_locations WHERE identity_name = ? AND deleted_at IS NULL"
        )) {
            lookup.setString(1, IDENTITY_NAME);
            try (ResultSet result = lookup.executeQuery()) {
                if (result.next()) {
                    return;
                }
            }
        }

        Timestamp now = Timestamp.from(Instant.now());
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO gam_locations ("
                        + "id, name, street, city, state, postal_code, country_code, "
                        + "identity_name, identity_street, identity_city, identity_state, "
                        + "identity_postal_code, identity_country_code, created_at, updated_at"
                        + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            insert.setObject(1, UUIDGenerator.generateUUIDV7());
            insert.setString(2, "São Mário");
            insert.setString(3, null);
            insert.setString(4, "Piracicaba");
            insert.setString(5, "SP");
            insert.setString(6, null);
            insert.setString(7, "BR");
            insert.setString(8, IDENTITY_NAME);
            insert.setString(9, "");
            insert.setString(10, "piracicaba");
            insert.setString(11, "sp");
            insert.setString(12, "");
            insert.setString(13, "br");
            insert.setTimestamp(14, now);
            insert.setTimestamp(15, now);
            insert.executeUpdate();
        }
    }
}
