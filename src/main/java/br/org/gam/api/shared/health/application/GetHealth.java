package br.org.gam.api.shared.health.application;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;

@Service
public class GetHealth {

    private static final String CONNECTIVITY_QUERY = "SELECT 1";
    private static final int QUERY_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    public GetHealth(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public HealthRDTO get() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(CONNECTIVITY_QUERY)) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) == 1
                        ? HealthRDTO.up()
                        : HealthRDTO.down();
            }
        } catch (SQLException | RuntimeException exception) {
            return HealthRDTO.down();
        }
    }
}
