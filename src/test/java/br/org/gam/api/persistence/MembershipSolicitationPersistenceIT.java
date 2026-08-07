package br.org.gam.api.persistence;

import br.org.gam.api.account.application.AccountMapper;
import br.org.gam.api.account.domain.Account;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.account.persistence.AccountRepository;
import br.org.gam.api.member.solicitation.domain.MembershipSolicitationStatus;
import br.org.gam.api.member.solicitation.persistence.MembershipSolicitationEntity;
import br.org.gam.api.member.solicitation.persistence.MembershipSolicitationRepository;
import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.shared.domain.GamName;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.PersistenceTest;
import br.org.gam.api.testing.integration.PostgreSQLIntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@PersistenceTest
@DisplayName("Persistence - Membership Solicitation Invariants")
class MembershipSolicitationPersistenceIT extends PostgreSQLIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private MembershipSolicitationRepository solicitationRepository;

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("REQ-MEMBER-SOL-004 - partial uniqueness -> one pending solicitation while rejected history remains reusable")
    void onePendingConstraintShouldPreserveRejectedHistoryAndPermitReapplication() {
        AccountEntity applicant = saveAccount("Solicitation persistence applicant");
        AccountEntity reviewer = saveAccount("Solicitation persistence reviewer");
        UUID firstId = inTransaction(() -> solicitationRepository.saveAndFlush(pending(applicant)).getId());

        assertThatThrownBy(() -> inTransaction(() ->
                solicitationRepository.saveAndFlush(pending(applicant))))
                .isInstanceOf(DataIntegrityViolationException.class);

        inTransaction(() -> {
            MembershipSolicitationEntity rejected = solicitationRepository.findById(firstId).orElseThrow();
            rejected.setStatus(MembershipSolicitationStatus.REJECTED);
            rejected.setReviewedBy(reviewer);
            rejected.setDecidedAt(Instant.now());
            rejected.setReviewReason("Applicant may submit a corrected request");
            return solicitationRepository.saveAndFlush(rejected);
        });

        UUID reappliedId = inTransaction(() -> solicitationRepository.saveAndFlush(pending(applicant)).getId());

        assertThat(reappliedId).isNotEqualTo(firstId);
        assertThat(solicitationCount(applicant.getId())).isEqualTo(2);
        assertThat(pendingSolicitationCount(applicant.getId())).isEqualTo(1);
        assertThat(solicitationStatus(firstId)).isEqualTo("REJECTED");
        assertThat(solicitationStatus(reappliedId)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("REQ-MEMBER-SOL-003 and REQ-MEMBER-SOL-009 - inconsistent approved decision -> database rejection and pending row preserved")
    void decisionConsistencyConstraintShouldRejectPartialApproval() {
        AccountEntity applicant = saveAccount("Partial approval applicant");
        UUID solicitationId = inTransaction(() ->
                solicitationRepository.saveAndFlush(pending(applicant)).getId());

        assertThatThrownBy(() -> inTransaction(() -> {
            MembershipSolicitationEntity partialApproval = solicitationRepository.findById(solicitationId)
                    .orElseThrow();
            partialApproval.setStatus(MembershipSolicitationStatus.APPROVED);
            return solicitationRepository.saveAndFlush(partialApproval);
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(solicitationStatus(solicitationId)).isEqualTo("PENDING");
        assertThat(pendingSolicitationCount(applicant.getId())).isEqualTo(1);
    }

    private AccountEntity saveAccount(String displayName) {
        return inTransaction(() -> accountRepository.saveAndFlush(account(displayName)));
    }

    private AccountEntity account(String displayName) {
        Account account = Account.register(
                GamEmail.of("solicitation-persistence-" + UUID.randomUUID() + "@example.com"),
                "{pbkdf2}hash",
                displayName
        );
        return accountMapper.domainToEntity(account);
    }

    private MembershipSolicitationEntity pending(AccountEntity applicant) {
        MembershipSolicitationEntity solicitation = new MembershipSolicitationEntity();
        solicitation.setId(UUIDGenerator.generateUUIDV7());
        solicitation.setAccount(applicant);
        solicitation.setName(new GamName("Ana", "Silva"));
        solicitation.setBirthDate(LocalDate.of(2000, 1, 1));
        solicitation.setPhoneNumber(GamPhoneNumber.fromString("+5519998877665"));
        solicitation.setGamEntryDate(LocalDate.of(2020, 1, 1));
        solicitation.setResidentialCity("Synthetic City");
        solicitation.setContactEmail(GamEmail.of("ana.fixture@example.com"));
        solicitation.setJustification("I want to participate in GAM activities");
        solicitation.setStatus(MembershipSolicitationStatus.PENDING);
        return solicitation;
    }

    private long solicitationCount(UUID accountId) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM membership_solicitations WHERE account_id = ?",
                Long.class,
                accountId
        ), "Expected solicitation count");
    }

    private long pendingSolicitationCount(UUID accountId) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM membership_solicitations "
                        + "WHERE account_id = ? AND status::text = 'PENDING' AND deleted_at IS NULL",
                Long.class,
                accountId
        ), "Expected pending solicitation count");
    }

    private String solicitationStatus(UUID solicitationId) {
        return jdbcTemplate.queryForObject(
                "SELECT status::text FROM membership_solicitations WHERE id = ?",
                String.class,
                solicitationId
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
}
