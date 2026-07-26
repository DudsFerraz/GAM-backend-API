package br.org.gam.api.persistence;

import br.org.gam.api.account.application.AccountMapper;
import br.org.gam.api.account.domain.Account;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.account.persistence.AccountRepository;
import br.org.gam.api.security.application.AccountDetails;
import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.PersistenceTest;
import br.org.gam.api.testing.integration.PostgreSQLIntegrationTest;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@FunctionalTest
@IntegrationTest
@PersistenceTest
@Import(PersistenceAuditingContractIT.FixedClockConfiguration.class)
@DisplayName("Persistence - Cross-domain auditing contract")
class PersistenceAuditingContractIT extends PostgreSQLIntegrationTest {

    private static final Instant TRUSTED_CREATION_INSTANT = Instant.parse("2026-02-03T04:05:06Z");
    private static final Instant TRUSTED_UPDATE_INSTANT = Instant.parse("2026-04-05T06:07:08Z");
    private static final Instant TRUSTED_DELETION_INSTANT = Instant.parse("2026-06-07T08:09:10Z");

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private TestClock applicationClock;

    private final List<UUID> accountIds = new ArrayList<>();
    private final List<UUID> activityLogIds = new ArrayList<>();
    private final List<UUID> roleIds = new ArrayList<>();

    @BeforeEach
    void resetApplicationClock() {
        applicationClock.setInstant(TRUSTED_CREATION_INSTANT);
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        activityLogIds.forEach(id -> jdbcTemplate.update("DELETE FROM activity_logs WHERE id = ?", id));
        roleIds.forEach(id -> jdbcTemplate.update("DELETE FROM roles WHERE id = ?", id));
        accountIds.forEach(id -> jdbcTemplate.update(
                "UPDATE accounts SET created_by = NULL, updated_by = NULL, deleted_by = NULL WHERE id = ?",
                id
        ));
        accountIds.forEach(id -> jdbcTemplate.update("DELETE FROM accounts WHERE id = ?", id));
    }

    @Test
    @DisplayName("REQ-PERSISTENCE-003 and REQ-PERSISTENCE-004 - authenticated create and soft delete -> trusted atomic metadata with update version preserved")
    void authenticatedCreateAndSoftDeleteShouldPreserveLatestNonDeletionVersion() {
        AccountEntity actor = persistAccount("audit-actor");
        authenticate(actor);

        UUID targetId = inTransaction(() ->
                accountRepository.saveAndFlush(newAccount("audited-target")).getId()
        );
        accountIds.add(targetId);

        Map<String, Object> creationAudit = auditRow(targetId);
        assertThat(creationAudit)
                .containsEntry("created_at", Timestamp.from(TRUSTED_CREATION_INSTANT))
                .containsEntry("created_by", actor.getId())
                .containsEntry("updated_at", Timestamp.from(TRUSTED_CREATION_INSTANT))
                .containsEntry("updated_by", actor.getId())
                .containsEntry("deleted_at", null)
                .containsEntry("deleted_by", null);

        applicationClock.setInstant(TRUSTED_UPDATE_INSTANT);
        inTransaction(() -> {
            AccountEntity target = accountRepository.findById(targetId).orElseThrow();
            target.setDisplayName("Audited target updated");
            return accountRepository.saveAndFlush(target);
        });

        Map<String, Object> updateAudit = auditRow(targetId);
        assertThat(updateAudit)
                .containsEntry("created_at", Timestamp.from(TRUSTED_CREATION_INSTANT))
                .containsEntry("created_by", actor.getId())
                .containsEntry("updated_at", Timestamp.from(TRUSTED_UPDATE_INSTANT))
                .containsEntry("updated_by", actor.getId())
                .containsEntry("deleted_at", null)
                .containsEntry("deleted_by", null);

        applicationClock.setInstant(TRUSTED_DELETION_INSTANT);
        inTransaction(() -> {
            AccountEntity target = accountRepository.findById(targetId).orElseThrow();
            accountRepository.delete(target);
            accountRepository.flush();
            return null;
        });

        Map<String, Object> deletionAudit = auditRow(targetId);
        assertThat(deletionAudit)
                .containsEntry("created_at", Timestamp.from(TRUSTED_CREATION_INSTANT))
                .containsEntry("updated_at", Timestamp.from(TRUSTED_UPDATE_INSTANT))
                .containsEntry("updated_by", actor.getId())
                .containsEntry("deleted_at", Timestamp.from(TRUSTED_DELETION_INSTANT))
                .containsEntry("deleted_by", actor.getId());
    }

    @Test
    @DisplayName("REQ-PERSISTENCE-003 and ADR-0018 - deletion actor without deletion timestamp -> database rejects invalid state")
    void deletionActorWithoutDeletionTimestampShouldBeRejected() {
        AccountEntity actor = persistAccount("invalid-deletion-actor");
        AccountEntity target = persistAccount("invalid-deletion-target");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE accounts SET deleted_by = ? WHERE id = ?",
                actor.getId(),
                target.getId()
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(auditRow(target.getId()))
                .containsEntry("deleted_at", null)
                .containsEntry("deleted_by", null);
    }

    @Test
    @DisplayName("REQ-PERSISTENCE-003 and ADR-0018 - non-Account full audit row with deletion actor but no timestamp -> database rejects invalid state")
    void nonAccountFullAuditRowWithoutDeletionTimestampShouldBeRejected() {
        AccountEntity actor = persistAccount("cross-domain-deletion-actor");
        UUID activeRoleId = jdbcTemplate.queryForObject(
                "SELECT id FROM roles WHERE deleted_at IS NULL ORDER BY id LIMIT 1",
                UUID.class
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE roles SET deleted_by = ? WHERE id = ?",
                actor.getId(),
                activeRoleId
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbcTemplate.queryForMap(
                "SELECT deleted_at, deleted_by FROM roles WHERE id = ?",
                activeRoleId
        )).containsEntry("deleted_at", null)
                .containsEntry("deleted_by", null);
    }

    @Test
    @DisplayName("REQ-PERSISTENCE-009 and ADR-0018 - physical audit actor deletion -> audited row and timestamps remain with nullable attribution")
    void physicalAuditActorDeletionShouldPreserveAuditedRowAndClearAttribution() {
        AccountEntity actor = persistAccount("physical-delete-actor");
        authenticate(actor);
        AccountEntity target = persistAccount("physical-delete-target");
        Map<String, Object> auditBeforeActorDeletion = auditRow(target.getId());

        jdbcTemplate.update("DELETE FROM accounts WHERE id = ?", actor.getId());
        accountIds.remove(actor.getId());

        Map<String, Object> preservedAudit = auditRow(target.getId());
        assertThat(preservedAudit)
                .containsEntry("created_at", auditBeforeActorDeletion.get("created_at"))
                .containsEntry("updated_at", auditBeforeActorDeletion.get("updated_at"))
                .containsEntry("created_by", null)
                .containsEntry("updated_by", null);
    }

    @Test
    @DisplayName("REQ-PERSISTENCE-009 and ADR-0018 - physical deletion actor removal -> deleted row and timestamps remain while deletedBy clears")
    void physicalDeletionActorRemovalShouldPreserveDeletedRowAndClearDeletionAttribution() {
        AccountEntity actor = persistAccount("physical-deletion-actor");
        authenticate(actor);
        AccountEntity target = persistAccount("physical-deleted-target");

        applicationClock.setInstant(TRUSTED_DELETION_INSTANT);
        inTransaction(() -> {
            AccountEntity activeTarget = accountRepository.findById(target.getId()).orElseThrow();
            accountRepository.delete(activeTarget);
            accountRepository.flush();
            return null;
        });
        Map<String, Object> deletedAudit = auditRow(target.getId());
        assertThat(deletedAudit)
                .containsEntry("deleted_at", Timestamp.from(TRUSTED_DELETION_INSTANT))
                .containsEntry("deleted_by", actor.getId());

        jdbcTemplate.update("DELETE FROM accounts WHERE id = ?", actor.getId());
        accountIds.remove(actor.getId());

        assertThat(auditRow(target.getId()))
                .containsEntry("created_at", deletedAudit.get("created_at"))
                .containsEntry("updated_at", deletedAudit.get("updated_at"))
                .containsEntry("deleted_at", deletedAudit.get("deleted_at"))
                .containsEntry("deleted_by", null);
    }

    @Test
    @DisplayName("REQ-PERSISTENCE-009 and ADR-0018 - physical activity actor deletion -> immutable actor UUID remains in activity history")
    void physicalActivityActorDeletionShouldPreserveImmutableActorUuid() {
        AccountEntity actor = persistAccount("activity-history-actor");
        UUID activityId = UUID.randomUUID();
        activityLogIds.add(activityId);
        UUID targetId = UUID.randomUUID();
        Timestamp occurredAt = Timestamp.from(Instant.parse("2026-04-03T02:01:00Z"));
        jdbcTemplate.update(
                "INSERT INTO activity_logs "
                        + "(id, occurred_at, actor_account_id, action, target_type, target_id, reason) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                activityId,
                occurredAt,
                actor.getId(),
                "PERSISTENCE_CONTRACT",
                "ACCOUNT",
                targetId,
                "Preserve immutable activity attribution"
        );

        jdbcTemplate.update("DELETE FROM accounts WHERE id = ?", actor.getId());
        accountIds.remove(actor.getId());

        assertThat(jdbcTemplate.queryForMap(
                "SELECT occurred_at, actor_account_id, target_id FROM activity_logs WHERE id = ?",
                activityId
        )).containsEntry("occurred_at", occurredAt)
                .containsEntry("actor_account_id", actor.getId())
                .containsEntry("target_id", targetId);
    }

    @Test
    @DisplayName("REQ-PERSISTENCE-009 and ADR-0018 - physical actor deletion for non-Account audit row -> row remains with nullable attribution")
    void physicalActorDeletionShouldPreserveNonAccountAuditRowAndClearAttribution() {
        AccountEntity actor = persistAccount("role-audit-actor");
        UUID roleId = UUID.randomUUID();
        roleIds.add(roleId);
        Timestamp auditInstant = Timestamp.from(Instant.parse("2026-05-04T03:02:01Z"));
        jdbcTemplate.update(
                "INSERT INTO roles "
                        + "(id, name, description, system_managed, created_at, created_by, updated_at, updated_by) "
                        + "VALUES (?, ?, ?, FALSE, ?, ?, ?, ?)",
                roleId,
                "AUDIT_ROLE_" + roleId,
                "Cross-domain audit attribution fixture",
                auditInstant,
                actor.getId(),
                auditInstant,
                actor.getId()
        );

        jdbcTemplate.update("DELETE FROM accounts WHERE id = ?", actor.getId());
        accountIds.remove(actor.getId());

        assertThat(jdbcTemplate.queryForMap(
                "SELECT created_at, created_by, updated_at, updated_by FROM roles WHERE id = ?",
                roleId
        )).containsEntry("created_at", auditInstant)
                .containsEntry("updated_at", auditInstant)
                .containsEntry("created_by", null)
                .containsEntry("updated_by", null);
    }

    private AccountEntity persistAccount(String label) {
        AccountEntity account = inTransaction(() -> accountRepository.saveAndFlush(newAccount(label)));
        accountIds.add(account.getId());
        return account;
    }

    private AccountEntity newAccount(String label) {
        Account account = Account.register(
                GamEmail.of(label + "-" + UUID.randomUUID() + "@example.com"),
                "{pbkdf2}hash",
                label
        );
        return accountMapper.domainToEntity(account);
    }

    private void authenticate(AccountEntity actor) {
        AccountDetails principal = new AccountDetails(actor, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(principal, "credentials", principal.getAuthorities())
        );
    }

    private Map<String, Object> auditRow(UUID accountId) {
        return jdbcTemplate.queryForMap(
                "SELECT created_at, created_by, updated_at, updated_by, deleted_at, deleted_by "
                        + "FROM accounts WHERE id = ?",
                accountId
        );
    }

    private <T> T inTransaction(TransactionCallback<T> callback) {
        return transactionTemplate.execute(status -> {
            T result = callback.run();
            entityManager.flush();
            entityManager.clear();
            return result;
        });
    }

    @FunctionalInterface
    private interface TransactionCallback<T> {
        T run();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        TestClock persistenceContractClock() {
            return new TestClock(TRUSTED_CREATION_INSTANT);
        }
    }

    static final class TestClock extends Clock {
        private Instant currentInstant;

        private TestClock(Instant currentInstant) {
            this.currentInstant = currentInstant;
        }

        void setInstant(Instant currentInstant) {
            this.currentInstant = currentInstant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(currentInstant, zone);
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }
}
