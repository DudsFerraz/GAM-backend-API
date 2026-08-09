package br.org.gam.api.health.application;

import br.org.gam.api.testing.annotation.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Health readiness database probe")
class HealthReadinessTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("REQ-OPS-011 - SELECT 1 returning one reports ready")
    void selectOneReturningOneReportsReady() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        assertThat(new HealthReadiness(jdbcTemplate).isReady()).isTrue();
        verify(jdbcTemplate).queryForObject("SELECT 1", Integer.class);
    }

    @Test
    @DisplayName("REQ-OPS-011 - database access failure reports not ready")
    void databaseAccessFailureReportsNotReady() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThat(new HealthReadiness(jdbcTemplate).isReady()).isFalse();
        verify(jdbcTemplate).queryForObject("SELECT 1", Integer.class);
    }
}
