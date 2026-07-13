package br.org.gam.api.rbac.accountRole.application.useCases;

import br.org.gam.api.rbac.accountRole.application.AccountRoleRDTO;
import br.org.gam.api.rbac.role.domain.SystemRole;
import br.org.gam.api.shared.exception.ConflictException;
import br.org.gam.api.shared.exception.ForbiddenOperationException;
import br.org.gam.api.shared.exception.NotFoundException;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.PersistenceTest;
import br.org.gam.api.testing.integration.PostgreSQLIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@FunctionalTest
@IntegrationTest
@PersistenceTest
@ActiveProfiles({"test", "maintenance"})
@TestPropertySource(properties = "maintenance.job=disabled")
@DisplayName("Functional - SUDO Maintenance")
class ManageSudoRoleIT extends PostgreSQLIntegrationTest {

    @Autowired
    private ManageSudoRole manageSudoRole;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Set<UUID> fixtureAccountIds = new LinkedHashSet<>();

    @AfterEach
    void cleanupFixtures() {
        jdbcTemplate.update("DELETE FROM activity_logs");
        for (UUID accountId : fixtureAccountIds) {
            jdbcTemplate.update("DELETE FROM account_roles WHERE account_id = ?", accountId);
            jdbcTemplate.update("DELETE FROM accounts WHERE id = ?", accountId);
        }
        fixtureAccountIds.clear();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-011 - assign-sudo -> one active assignment and one normalized audit event")
    void assignSudoShouldCreateOneActiveAssignmentAndAuditEvent() {
        UUID accountId = insertAccount();
        UUID sudoRoleId = roleId(SystemRole.SUDO.getCode());
        String reason = "Grant recovery access";

        AccountRoleRDTO response = manageSudoRole.assignSudo(accountId, " " + reason + " ");

        UUID assignmentId = activeAssignmentId(accountId, sudoRoleId);
        assertThat(response.assignmentId()).isEqualTo(assignmentId);
        assertThat(activeAssignmentCount(accountId, sudoRoleId)).isEqualTo(1L);
        assertThat(accountRoleActivityCount()).isEqualTo(1L);
        assertAccountRoleActivity(
                assignmentId,
                "ACCOUNT_ROLE_ADDED",
                accountId,
                sudoRoleId,
                SystemRole.SUDO.getCode(),
                reason
        );
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-011 - duplicate active SUDO assignment -> conflict without duplicate or audit")
    void duplicateActiveSudoAssignmentShouldReturnConflictWithoutMutationOrAudit() {
        UUID accountId = insertAccount();
        UUID sudoRoleId = roleId(SystemRole.SUDO.getCode());
        UUID existingAssignmentId = insertSudoAssignment(accountId);

        assertThatThrownBy(() -> manageSudoRole.assignSudo(accountId, "Grant duplicate recovery access"))
                .isInstanceOf(ConflictException.class);

        assertThat(activeAssignmentCount(accountId, sudoRoleId)).isEqualTo(1L);
        assertThat(activeAssignmentId(accountId, sudoRoleId)).isEqualTo(existingAssignmentId);
        assertThat(accountRoleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-012 - remove-sudo with another active SUDO -> soft-delete and one audit event")
    void removeSudoWithAnotherActiveSudoShouldSoftDeleteAndAudit() {
        UUID targetAccountId = insertAccount();
        UUID otherAccountId = insertAccount();
        UUID sudoRoleId = roleId(SystemRole.SUDO.getCode());
        UUID targetAssignmentId = insertSudoAssignment(targetAccountId);
        insertSudoAssignment(otherAccountId);
        String reason = "Remove retired recovery access";

        manageSudoRole.removeSudo(targetAccountId, " " + reason + " ");

        assertThat(activeAssignmentCount(targetAccountId, sudoRoleId)).isZero();
        assertThat(activeAssignmentCount(otherAccountId, sudoRoleId)).isEqualTo(1L);
        assertThat(assignmentIsDeleted(targetAssignmentId)).isTrue();
        assertThat(accountRoleActivityCount()).isEqualTo(1L);
        assertAccountRoleActivity(
                targetAssignmentId,
                "ACCOUNT_ROLE_REMOVED",
                targetAccountId,
                sudoRoleId,
                SystemRole.SUDO.getCode(),
                reason
        );
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-012 - missing active SUDO assignment -> not found without mutation or audit")
    void missingActiveSudoAssignmentShouldReturnNotFoundWithoutMutationOrAudit() {
        UUID accountId = insertAccount();
        UUID sudoRoleId = roleId(SystemRole.SUDO.getCode());

        assertThatThrownBy(() -> manageSudoRole.removeSudo(accountId, "Remove missing recovery access"))
                .isInstanceOf(NotFoundException.class);

        assertThat(activeAssignmentCount(accountId, sudoRoleId)).isZero();
        assertThat(accountRoleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-013 - last active SUDO assignment -> forbidden and remains active")
    void lastActiveSudoAssignmentShouldBeForbiddenWithoutMutationOrAudit() {
        UUID accountId = insertAccount();
        UUID assignmentId = insertSudoAssignment(accountId);
        UUID sudoRoleId = roleId(SystemRole.SUDO.getCode());

        assertThatThrownBy(() -> manageSudoRole.removeSudo(accountId, "Remove final recovery access"))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("Cannot remove the last active SUDO account.");

        assertThat(activeAssignmentCount(accountId, sudoRoleId)).isEqualTo(1L);
        assertThat(assignmentIsDeleted(assignmentId)).isFalse();
        assertThat(accountRoleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-011 - reassign after drop -> creates a new active assignment identity")
    void reassignAfterDropShouldCreateNewActiveAssignmentIdentity() {
        UUID targetAccountId = insertAccount();
        UUID otherAccountId = insertAccount();
        UUID sudoRoleId = roleId(SystemRole.SUDO.getCode());
        UUID historicalAssignmentId = insertSudoAssignment(targetAccountId);
        insertSudoAssignment(otherAccountId);

        manageSudoRole.removeSudo(targetAccountId, "Remove temporary recovery access");
        AccountRoleRDTO reassigned = manageSudoRole.assignSudo(targetAccountId, "Restore recovery access");

        UUID activeAssignmentId = activeAssignmentId(targetAccountId, sudoRoleId);
        assertThat(activeAssignmentId).isNotEqualTo(historicalAssignmentId);
        assertThat(reassigned.assignmentId()).isEqualTo(activeAssignmentId);
        assertThat(totalAssignmentCount(targetAccountId, sudoRoleId)).isEqualTo(2L);
        assertThat(activeAssignmentCount(targetAccountId, sudoRoleId)).isEqualTo(1L);
        assertThat(accountRoleActivityCount(historicalAssignmentId, "ACCOUNT_ROLE_REMOVED")).isEqualTo(1L);
        assertThat(accountRoleActivityCount(activeAssignmentId, "ACCOUNT_ROLE_ADDED")).isEqualTo(1L);
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-010 - missing or soft-deleted Account -> no SUDO mutation")
    void missingOrSoftDeletedAccountShouldNotReceiveOrLoseSudo() {
        UUID missingAccountId = UUID.randomUUID();
        assertThatThrownBy(() -> manageSudoRole.assignSudo(missingAccountId, "Grant recovery access"))
                .isInstanceOf(NotFoundException.class);

        UUID deletedAccountId = insertAccount();
        UUID deletedAssignmentId = insertSudoAssignment(deletedAccountId);
        softDeleteAccount(deletedAccountId);

        assertThatThrownBy(() -> manageSudoRole.removeSudo(deletedAccountId, "Remove recovery access"))
                .isInstanceOf(NotFoundException.class);

        assertThat(assignmentIsDeleted(deletedAssignmentId)).isFalse();
        assertThat(accountRoleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-013 and ADR-0002 - concurrent SUDO removals preserve one active assignment")
    void concurrentSudoRemovalsShouldPreserveOneActiveAssignment() throws Exception {
        UUID firstAccountId = insertAccount();
        UUID secondAccountId = insertAccount();
        insertSudoAssignment(firstAccountId);
        insertSudoAssignment(secondAccountId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Throwable> first = executor.submit(() -> attemptRemoval(firstAccountId, ready, start));
        Future<Throwable> second = executor.submit(() -> attemptRemoval(secondAccountId, ready, start));

        Throwable firstFailure;
        Throwable secondFailure;
        try {
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            firstFailure = first.get(15, TimeUnit.SECONDS);
            secondFailure = second.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        Throwable[] failures = {firstFailure, secondFailure};
        assertThat(Arrays.stream(failures).filter(Objects::isNull).count()).isEqualTo(1L);
        assertThat(Arrays.stream(failures)
                .filter(failure -> failure instanceof ForbiddenOperationException)
                .count()).isEqualTo(1L);
        assertThat(Arrays.stream(failures)
                .filter(failure -> failure != null && !(failure instanceof ForbiddenOperationException))
                .count()).isZero();
        assertThat(activeSudoCount()).isEqualTo(1L);
        assertThat(accountRoleActivityCount()).isEqualTo(1L);
    }

    private Throwable attemptRemoval(UUID accountId, CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                return new AssertionError("Concurrent SUDO removal did not start.");
            }
            manageSudoRole.removeSudo(accountId, "Concurrent recovery rotation");
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private UUID insertAccount() {
        UUID accountId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO accounts (id, email, password_hash, display_name, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                accountId,
                "sudo-maintenance-" + accountId + "@example.com",
                "encoded-password",
                "SUDO Maintenance Account",
                now,
                now
        );
        fixtureAccountIds.add(accountId);
        return accountId;
    }

    private UUID insertSudoAssignment(UUID accountId) {
        UUID assignmentId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO account_roles (id, account_id, role_id, created_at) VALUES (?, ?, ?, ?)",
                assignmentId,
                accountId,
                roleId(SystemRole.SUDO.getCode()),
                Timestamp.from(Instant.now())
        );
        return assignmentId;
    }

    private void softDeleteAccount(UUID accountId) {
        jdbcTemplate.update("UPDATE accounts SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", accountId);
    }

    private UUID roleId(String roleName) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM roles WHERE name = ? AND deleted_at IS NULL",
                UUID.class,
                roleName
        );
    }

    private UUID activeAssignmentId(UUID accountId, UUID roleId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM account_roles WHERE account_id = ? AND role_id = ? AND deleted_at IS NULL",
                UUID.class,
                accountId,
                roleId
        );
    }

    private long activeAssignmentCount(UUID accountId, UUID roleId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_roles "
                        + "WHERE account_id = ? AND role_id = ? AND deleted_at IS NULL",
                Long.class,
                accountId,
                roleId
        );
    }

    private long totalAssignmentCount(UUID accountId, UUID roleId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_roles WHERE account_id = ? AND role_id = ?",
                Long.class,
                accountId,
                roleId
        );
    }

    private long activeSudoCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_roles account_role "
                        + "JOIN accounts account ON account.id = account_role.account_id "
                        + "JOIN roles role ON role.id = account_role.role_id "
                        + "WHERE account_role.deleted_at IS NULL "
                        + "AND account.deleted_at IS NULL "
                        + "AND role.deleted_at IS NULL "
                        + "AND role.name = ?",
                Long.class,
                SystemRole.SUDO.getCode()
        );
    }

    private boolean assignmentIsDeleted(UUID assignmentId) {
        return jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM account_roles WHERE id = ?",
                Boolean.class,
                assignmentId
        );
    }

    private long accountRoleActivityCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_logs WHERE target_type = 'ACCOUNT_ROLE'",
                Long.class
        );
    }

    private long accountRoleActivityCount(UUID assignmentId, String action) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_logs "
                        + "WHERE target_type = 'ACCOUNT_ROLE' AND action = ? AND target_id = ?",
                Long.class,
                action,
                assignmentId
        );
    }

    private void assertAccountRoleActivity(
            UUID assignmentId,
            String action,
            UUID accountId,
            UUID roleId,
            String roleName,
            String reason
    ) {
        Map<String, Object> activity = jdbcTemplate.queryForMap(
                "SELECT action, reason, metadata ->> 'accountId' AS account_id, "
                        + "metadata ->> 'roleId' AS role_id, metadata ->> 'roleName' AS role_name "
                        + "FROM activity_logs WHERE target_type = 'ACCOUNT_ROLE' AND target_id = ?",
                assignmentId
        );

        assertThat(activity)
                .containsEntry("action", action)
                .containsEntry("reason", reason)
                .containsEntry("account_id", accountId.toString())
                .containsEntry("role_id", roleId.toString())
                .containsEntry("role_name", roleName);
    }
}
