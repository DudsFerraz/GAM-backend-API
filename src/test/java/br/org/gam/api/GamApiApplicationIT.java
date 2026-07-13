package br.org.gam.api;

import br.org.gam.api.rbac.accountRole.application.useCases.ManageSudoRole;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.PersistenceTest;
import br.org.gam.api.testing.integration.PostgreSQLIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@PersistenceTest
@DisplayName("Integration - Application Context")
class GamApiApplicationIT extends PostgreSQLIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("PostgreSQL Testcontainer with Flyway migrations -> application context loads")
    void contextLoads() {
    }

    @Test
    @DisplayName("non-maintenance profile -> SUDO mutator is unavailable")
    void sudoMutatorShouldBeUnavailableOutsideMaintenanceProfile() {
        assertThat(applicationContext.getBeansOfType(ManageSudoRole.class)).isEmpty();
    }
}
