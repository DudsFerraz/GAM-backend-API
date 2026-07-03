package br.org.gam.api.persistence;

import br.org.gam.api.account.application.AccountMapper;
import br.org.gam.api.account.domain.Account;
import br.org.gam.api.account.domain.MyEmail;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.account.persistence.AccountRepository;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.event.persistence.EventRepository;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.member.persistence.MemberRepository;
import br.org.gam.api.presence.persistence.PresenceEntity;
import br.org.gam.api.presence.persistence.PresenceRepository;
import br.org.gam.api.shared.domain.Name;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import br.org.gam.api.shared.phonenumber.MyPhoneNumber;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.PersistenceTest;
import br.org.gam.api.testing.integration.PostgreSQLIntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@PersistenceTest
@DisplayName("Persistence - Repository Schema Behavior")
class PersistenceRepositoryIT extends PostgreSQLIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private PresenceRepository presenceRepository;

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Nested
    @PersistenceTest
    @DisplayName("Persistence")
    class Persistence {

        @Test
        @DisplayName("unique email with soft delete -> duplicate active rejected and deleted email reusable")
        void uniqueEmailWithSoftDeleteShouldRejectActiveDuplicateAndAllowDeletedDuplicate() {
            String email = uniqueEmail();
            UUID originalId = inTransaction(() -> accountRepository.saveAndFlush(account(email, "Original Account")).getId());

            assertThatThrownBy(() -> inTransaction(() -> accountRepository.saveAndFlush(account(email, "Duplicate Account"))))
                    .isInstanceOf(DataIntegrityViolationException.class);

            inTransaction(() -> {
                AccountEntity original = accountRepository.findById(originalId).orElseThrow();
                accountRepository.delete(original);
                accountRepository.flush();
                return null;
            });

            UUID replacementId = inTransaction(() -> accountRepository.saveAndFlush(account(email, "Replacement Account")).getId());

            AccountEntity replacement = inTransaction(() -> accountRepository.findByEmail(MyEmail.of(email)).orElseThrow());
            assertThat(replacement.getId()).isEqualTo(replacementId);
            assertThat(deletedAccountIds())
                    .extracting(AccountEntity::getId)
                    .contains(originalId);
        }

        @Test
        @DisplayName("presence with missing member -> foreign key violation")
        void presenceWithMissingMemberShouldFailForeignKeyConstraint() {
            UUID eventId = inTransaction(() -> eventRepository.saveAndFlush(event("Foreign Key Event")).getId());
            EventEntity event = inTransaction(() -> eventRepository.findById(eventId).orElseThrow());
            UUID missingMemberId = UUIDGenerator.generateUUIDV4();

            assertThatThrownBy(() -> inTransaction(() -> {
                PresenceEntity presence = new PresenceEntity();
                presence.setId(UUIDGenerator.generateUUIDV7());
                presence.setEvent(event);
                presence.setMember(memberReference(missingMemberId));
                presence.setObservations("Invalid member reference");
                return presenceRepository.saveAndFlush(presence);
            })).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("domain generated id and auditing fields -> persisted and reloaded")
        void generatedIdAndAuditingFieldsShouldBePersisted() {
            AccountEntity account = account("generated-audit-" + UUID.randomUUID() + "@example.com", "Generated Audit");

            AccountEntity persisted = inTransaction(() -> accountRepository.saveAndFlush(account));

            AccountEntity reloaded = inTransaction(() -> accountRepository.findById(persisted.getId()).orElseThrow());
            assertThat(reloaded.getId()).isNotNull();
            assertThat(reloaded.getCreatedAt()).isNotNull();
            assertThat(reloaded.getUpdatedAt()).isNotNull();
            assertThat(reloaded.getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("repository query methods -> persisted account, member, and presence are discoverable")
        void repositoryQueryMethodsShouldFindPersistedAssociations() {
            AccountEntity account = inTransaction(() -> accountRepository.saveAndFlush(account(uniqueEmail(), "Repository Query")));
            MemberEntity member = inTransaction(() -> memberRepository.saveAndFlush(member(account, MemberStatus.ACTIVE)));
            EventEntity event = inTransaction(() -> eventRepository.saveAndFlush(event("Repository Query Event")));
            PresenceEntity presence = inTransaction(() -> presenceRepository.saveAndFlush(presence(member, event)));

            assertThat(accountRepository.existsByEmail(account.getEmail())).isTrue();
            assertThat(accountRepository.findByEmail(account.getEmail())).hasValueSatisfying(found ->
                    assertThat(found.getId()).isEqualTo(account.getId()));
            assertThat(memberRepository.existsByAccountId(account.getId())).isTrue();
            assertThat(presenceRepository.existsByMember_IdAndEvent_Id(member.getId(), event.getId())).isTrue();
            assertThat(presenceRepository.findByMember_IdAndEvent_Id(member.getId(), event.getId()))
                    .hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(presence.getId()));
        }

        @Test
        @DisplayName("soft deleted account -> hidden from default reads and visible through deleted query")
        void softDeletedAccountShouldBeHiddenFromDefaultReadsAndVisibleThroughDeletedQuery() {
            UUID accountId = inTransaction(() -> accountRepository.saveAndFlush(account(uniqueEmail(), "Soft Delete")).getId());

            inTransaction(() -> {
                AccountEntity account = accountRepository.findById(accountId).orElseThrow();
                accountRepository.delete(account);
                accountRepository.flush();
                return null;
            });

            assertThat(inTransaction(() -> accountRepository.findById(accountId))).isEmpty();
            assertThat(deletedAccountIds())
                    .extracting(AccountEntity::getId)
                    .contains(accountId);
        }
    }

    private AccountEntity account(String email, String displayName) {
        Account account = Account.register(MyEmail.of(email), "{bcrypt}hash", displayName);
        return accountMapper.domainToEntity(account);
    }

    private MemberEntity member(AccountEntity account, MemberStatus status) {
        MemberEntity member = new MemberEntity();
        member.setId(UUIDGenerator.generateUUIDV7());
        member.setAccount(account);
        member.setName(new Name("Ana", "Silva"));
        member.setBirthDate(LocalDate.of(1995, 1, 10));
        member.setPhoneNumber(MyPhoneNumber.fromString("+5511987654321"));
        member.setStatus(status);
        return member;
    }

    private EventEntity event(String title) {
        Instant begin = Instant.now().plusSeconds(3600);

        EventEntity event = new EventEntity();
        event.setId(UUIDGenerator.generateUUIDV7());
        event.setTitle(title + " " + UUID.randomUUID());
        event.setDescription("Persistence integration event");
        event.setType(EventType.GENERIC);
        event.setStatus(EventStatus.SCHEDULED);
        event.setBeginDate(begin);
        event.setEndDate(begin.plusSeconds(3600));
        return event;
    }

    private PresenceEntity presence(MemberEntity member, EventEntity event) {
        PresenceEntity presence = new PresenceEntity();
        presence.setId(UUIDGenerator.generateUUIDV7());
        presence.setMember(member);
        presence.setEvent(event);
        presence.setObservations("Repository query presence");
        return presence;
    }

    private MemberEntity memberReference(UUID id) {
        MemberEntity member = new MemberEntity();
        member.setId(id);
        return member;
    }

    private String uniqueEmail() {
        return "it-" + UUID.randomUUID() + "@example.com";
    }

    private List<AccountEntity> deletedAccountIds() {
        return entityManager
                .createNativeQuery("SELECT * FROM accounts WHERE deleted_at IS NOT NULL", AccountEntity.class)
                .getResultList();
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
