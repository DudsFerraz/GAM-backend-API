package br.org.gam.api.rbac.accountRole;

import br.org.gam.api.rbac.accountRole.persistence.AccountRoleRepository;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.PersistenceTest;
import br.org.gam.api.testing.integration.PostgreSQLIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@FunctionalTest
@IntegrationTest
@PersistenceTest
@DisplayName("Persistence - Account Role Assignments")
class AccountRolePersistenceIT extends PostgreSQLIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AccountRoleRepository accountRoleRepository;

    private UUID accountId;
    private UUID actorId;
    private UUID roleId;

    @BeforeEach
    void createFixtures() {
        accountId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        roleId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());

        insertAccount(accountId, "Account Role Persistence");
        insertAccount(actorId, "Account Role Actor");
        jdbcTemplate.update(
                "INSERT INTO roles "
                        + "(id, name, description, system_managed, created_at, updated_at) "
                        + "VALUES (?, ?, ?, FALSE, ?, ?)",
                roleId,
                "PERSISTENCE_" + roleId.toString().substring(0, 8),
                "Persistence test role",
                now,
                now
        );
    }

    @AfterEach
    void deleteFixtures() {
        jdbcTemplate.update("DELETE FROM account_roles WHERE account_id = ?", accountId);
        jdbcTemplate.update("DELETE FROM roles WHERE id = ?", roleId);
        jdbcTemplate.update("DELETE FROM accounts WHERE id = ?", accountId);
        jdbcTemplate.update("DELETE FROM accounts WHERE id = ?", actorId);
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-004 - soft-deleted assignment remains historical but is hidden from active reads")
    void softDeletedAssignmentShouldRemainHistoricalAndBeHiddenFromActiveReads() {
        UUID assignmentId = insertAssignment();

        jdbcTemplate.update(
                "UPDATE account_roles SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                assignmentId
        );

        assertThat(accountRoleRepository.findByAccount_IdAndRole_Id(accountId, roleId)).isEmpty();
        assertThat(accountRoleRepository.findAllByAccount_Id(accountId)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_roles WHERE id = ?",
                Long.class,
                assignmentId
        )).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM account_roles WHERE id = ?",
                Boolean.class,
                assignmentId
        )).isTrue();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-003 - re-adding after drop creates a new active assignment identity")
    void readdingAfterDropShouldCreateNewActiveAssignmentIdentity() {
        UUID historicalAssignmentId = insertAssignment();
        jdbcTemplate.update(
                "UPDATE account_roles SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                historicalAssignmentId
        );

        UUID newAssignmentId = insertAssignment();

        assertThat(newAssignmentId).isNotEqualTo(historicalAssignmentId);
        assertThat(accountRoleRepository.findByAccount_IdAndRole_Id(accountId, roleId))
                .get()
                .extracting(assignment -> assignment.getId())
                .isEqualTo(newAssignmentId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_roles WHERE account_id = ? AND role_id = ?",
                Long.class,
                accountId,
                roleId
        )).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_roles "
                        + "WHERE account_id = ? AND role_id = ? AND deleted_at IS NULL",
                Long.class,
                accountId,
                roleId
        )).isEqualTo(1L);
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-003 - database prevents two active assignments for one Account and Role")
    void databaseShouldRejectDuplicateActiveAssignment() {
        insertAssignment();

        assertThatThrownBy(this::insertAssignment)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Persistence history - deleting an audit actor preserves the Account-role assignment")
    void deletingAuditActorShouldPreserveAccountRoleHistory() {
        UUID assignmentId = insertAssignment();

        jdbcTemplate.update(
                "UPDATE account_roles SET created_by = ?, deleted_at = CURRENT_TIMESTAMP, deleted_by = ? WHERE id = ?",
                actorId,
                actorId,
                assignmentId
        );
        jdbcTemplate.update("DELETE FROM accounts WHERE id = ?", actorId);

        Map<String, Object> history = jdbcTemplate.queryForMap(
                "SELECT created_by, deleted_by, deleted_at FROM account_roles WHERE id = ?",
                assignmentId
        );
        assertThat(history.get("created_by")).isNull();
        assertThat(history.get("deleted_by")).isNull();
        assertThat(history.get("deleted_at")).isNotNull();
    }

    private void insertAccount(UUID id, String displayName) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO accounts "
                        + "(id, email, password_hash, display_name, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id,
                "account-role-persistence-" + id + "@example.com",
                "encoded-password",
                displayName,
                now,
                now
        );
    }

    private UUID insertAssignment() {
        UUID assignmentId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO account_roles (id, account_id, role_id, created_at) VALUES (?, ?, ?, ?)",
                assignmentId,
                accountId,
                roleId,
                Timestamp.from(Instant.now())
        );
        return assignmentId;
    }
}
