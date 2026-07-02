package br.org.gam.api;

import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.PersistenceTest;
import br.org.gam.api.testing.integration.PostgreSQLIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@IntegrationTest
@PersistenceTest
@DisplayName("Integration - Application Context")
class GamApiApplicationIT extends PostgreSQLIntegrationTest {

    @Test
    @DisplayName("PostgreSQL Testcontainer with Flyway migrations -> application context loads")
    void contextLoads() {
    }
}
