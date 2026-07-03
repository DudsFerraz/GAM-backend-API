package br.org.gam.api.persistence;

import br.org.gam.api.account.application.AccountMapper;
import br.org.gam.api.account.application.search.AccountSearchFilterConverter;
import br.org.gam.api.account.domain.Account;
import br.org.gam.api.account.domain.MyEmail;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.account.persistence.AccountRepository;
import br.org.gam.api.event.application.search.EventSearchFilterConverter;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.event.persistence.EventRepository;
import br.org.gam.api.event.persistence.EventSecuritySpecification;
import br.org.gam.api.member.application.search.MemberSearchFilterConverter;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.member.persistence.MemberRepository;
import br.org.gam.api.member.persistence.MemberSecuritySpecification;
import br.org.gam.api.rbac.Permission.persistence.PermissionEntity;
import br.org.gam.api.rbac.Permission.persistence.PermissionRepository;
import br.org.gam.api.shared.domain.Name;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import br.org.gam.api.shared.phonenumber.MyPhoneNumber;
import br.org.gam.api.shared.specification.ComparationMethods;
import br.org.gam.api.shared.specification.SpecificationFilterDTO;
import br.org.gam.api.shared.specification.SearchDTO;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.PersistenceTest;
import br.org.gam.api.testing.integration.PostgreSQLIntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@PersistenceTest
@DisplayName("Persistence - Specifications and Filters")
class SpecificationPersistenceIT extends PostgreSQLIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private AccountSearchFilterConverter accountSearchFilterConverter;

    @Autowired
    private MemberSearchFilterConverter memberSearchFilterConverter;

    @Autowired
    private EventSearchFilterConverter eventSearchFilterConverter;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Nested
    @PersistenceTest
    @DisplayName("Persistence")
    class Persistence {

        @Test
        @DisplayName("account filter converter and specification builder -> dynamic query matches persisted row")
        void accountFilterConverterAndSpecificationBuilderShouldQueryPersistedRows() {
            String targetDisplayName = "Account Spec " + UUID.randomUUID();
            AccountEntity target = inTransaction(() -> accountRepository.saveAndFlush(account(uniqueEmail(), targetDisplayName)));
            inTransaction(() -> accountRepository.saveAndFlush(account(uniqueEmail(), "Other Account Spec")));

            Specification<AccountEntity> specification = accountSearchFilterConverter.convert(new SearchDTO(
                    List.of(new SpecificationFilterDTO("displayName", targetDisplayName, ComparationMethods.EQUALS))
            ));

            List<AccountEntity> results = inTransaction(() -> accountRepository.findAll(specification));

            assertThat(results)
                    .extracting(AccountEntity::getId)
                    .containsExactly(target.getId());
        }

        @Test
        @DisplayName("account search filter converter -> email LIKE matches converted email column")
        void accountSearchFilterConverterShouldQueryByPartialEmail() {
            String localPart = "partial" + UUID.randomUUID().toString().replace("-", "");
            AccountEntity target = inTransaction(() -> accountRepository.saveAndFlush(account(localPart + "@example.com", "Partial Email Target")));
            inTransaction(() -> accountRepository.saveAndFlush(account(uniqueEmail(), "Other Account Spec")));

            Specification<AccountEntity> specification = accountSearchFilterConverter.convert(new SearchDTO(
                    List.of(new SpecificationFilterDTO("email", localPart.substring(0, 12), ComparationMethods.LIKE))
            ));

            List<AccountEntity> results = inTransaction(() -> accountRepository.findAll(specification));

            assertThat(results)
                    .extracting(AccountEntity::getId)
                    .containsExactly(target.getId());
        }

        @Test
        @DisplayName("member filter converter and specification builder -> enum filter matches persisted row")
        void memberFilterConverterAndSpecificationBuilderShouldQueryEnumRows() {
            MemberEntity active = saveMemberWithAccount(MemberStatus.ACTIVE);
            saveMemberWithAccount(MemberStatus.PENDENT);

            Specification<MemberEntity> specification = memberSearchFilterConverter.convert(new SearchDTO(
                    List.of(new SpecificationFilterDTO("status", "ACTIVE", ComparationMethods.EQUALS))
            ));

            List<MemberEntity> results = inTransaction(() -> memberRepository.findAll(specification));

            assertThat(results)
                    .extracting(MemberEntity::getId)
                    .contains(active.getId());
            assertThat(results)
                    .extracting(MemberEntity::getStatus)
                    .containsOnly(MemberStatus.ACTIVE);
        }

        @Test
        @DisplayName("member search filter converter -> public name LIKE matches first and surname")
        void memberSearchFilterConverterShouldQueryByPublicNameAlias() {
            MemberEntity target = saveMemberWithAccount(MemberStatus.ACTIVE);

            Specification<MemberEntity> specification = memberSearchFilterConverter.convert(new SearchDTO(
                    List.of(new SpecificationFilterDTO("name", "souza", ComparationMethods.LIKE))
            ));

            List<MemberEntity> results = inTransaction(() -> memberRepository.findAll(specification));

            assertThat(results)
                    .extracting(MemberEntity::getId)
                    .contains(target.getId());
        }

        @Test
        @DisplayName("member search filter converter -> phoneNumber LIKE matches converted phone column")
        void memberSearchFilterConverterShouldQueryByPartialPhoneNumber() {
            MemberEntity target = saveMemberWithAccount(MemberStatus.ACTIVE, "+5511988880001");
            saveMemberWithAccount(MemberStatus.ACTIVE, "+5511977770002");

            Specification<MemberEntity> specification = memberSearchFilterConverter.convert(new SearchDTO(
                    List.of(new SpecificationFilterDTO("phoneNumber", "88880001", ComparationMethods.LIKE))
            ));

            List<MemberEntity> results = inTransaction(() -> memberRepository.findAll(specification));

            assertThat(results)
                    .extracting(MemberEntity::getId)
                    .containsExactly(target.getId());
        }

        @Test
        @DisplayName("event security specification -> public and authorized events visible")
        void eventSecuritySpecificationShouldHideAndShowRecordsByRequiredPermission() {
            PermissionEntity permission = inTransaction(() -> permissionRepository.saveAndFlush(permission("EVENT_PRIVATE_" + UUID.randomUUID())));
            EventEntity publicEvent = inTransaction(() -> eventRepository.saveAndFlush(event("Public Event", null)));
            EventEntity privateEvent = inTransaction(() -> eventRepository.saveAndFlush(event("Private Event", permission)));

            List<EventEntity> anonymousResults = inTransaction(() ->
                    eventRepository.findAll(EventSecuritySpecification.canGetEvent(Set.of())));
            List<EventEntity> authorizedResults = inTransaction(() ->
                    eventRepository.findAll(EventSecuritySpecification.canGetEvent(Set.of(permission.getName()))));

            assertThat(anonymousResults)
                    .extracting(EventEntity::getId)
                    .contains(publicEvent.getId())
                    .doesNotContain(privateEvent.getId());
            assertThat(authorizedResults)
                    .extracting(EventEntity::getId)
                    .contains(publicEvent.getId(), privateEvent.getId());
        }

        @Test
        @DisplayName("member security specification -> non active records hidden unless authority exists")
        void memberSecuritySpecificationShouldHideNonActiveMembersWithoutAuthority() {
            MemberEntity active = saveMemberWithAccount(MemberStatus.ACTIVE);
            MemberEntity pendent = saveMemberWithAccount(MemberStatus.PENDENT);

            List<MemberEntity> defaultResults = inTransaction(() ->
                    memberRepository.findAll(MemberSecuritySpecification.canGetMember(Set.of())));
            List<MemberEntity> privilegedResults = inTransaction(() ->
                    memberRepository.findAll(MemberSecuritySpecification.canGetMember(Set.of("MEMBER_GET_NON_ACTIVE"))));

            assertThat(defaultResults)
                    .extracting(MemberEntity::getId)
                    .contains(active.getId())
                    .doesNotContain(pendent.getId());
            assertThat(privilegedResults)
                    .extracting(MemberEntity::getId)
                    .contains(active.getId(), pendent.getId());
        }

        @Test
        @DisplayName("event filter converter and specification builder -> id filter matches persisted row")
        void eventFilterConverterAndSpecificationBuilderShouldQueryById() {
            EventEntity target = inTransaction(() -> eventRepository.saveAndFlush(event("Target Without Location", null)));
            EventEntity other = inTransaction(() -> eventRepository.saveAndFlush(event("Other Without Location", null)));

            Specification<EventEntity> specification = eventSearchFilterConverter.convert(new SearchDTO(
                    List.of(new SpecificationFilterDTO("id", target.getId().toString(), ComparationMethods.EQUALS))
            ));

            List<EventEntity> results = inTransaction(() -> eventRepository.findAll(specification));

            assertThat(results)
                    .extracting(EventEntity::getId)
                    .containsExactly(target.getId())
                    .doesNotContain(other.getId());
        }
    }

    private MemberEntity saveMemberWithAccount(MemberStatus status) {
        AccountEntity account = inTransaction(() -> accountRepository.saveAndFlush(account(uniqueEmail(), "Member " + status)));
        return inTransaction(() -> memberRepository.saveAndFlush(member(account, status, "+5511912345678")));
    }

    private MemberEntity saveMemberWithAccount(MemberStatus status, String phoneNumber) {
        AccountEntity account = inTransaction(() -> accountRepository.saveAndFlush(account(uniqueEmail(), "Member " + status)));
        return inTransaction(() -> memberRepository.saveAndFlush(member(account, status, phoneNumber)));
    }

    private AccountEntity account(String email, String displayName) {
        Account account = Account.register(MyEmail.of(email), "{bcrypt}hash", displayName);
        return accountMapper.domainToEntity(account);
    }

    private MemberEntity member(AccountEntity account, MemberStatus status, String phoneNumber) {
        MemberEntity member = new MemberEntity();
        member.setId(UUIDGenerator.generateUUIDV7());
        member.setAccount(account);
        member.setName(new Name("Ian", "Souza"));
        member.setBirthDate(LocalDate.of(1998, 3, 20));
        member.setPhoneNumber(MyPhoneNumber.fromString(phoneNumber));
        member.setStatus(status);
        return member;
    }

    private PermissionEntity permission(String name) {
        PermissionEntity permission = new PermissionEntity();
        permission.setId(UUIDGenerator.generateUUIDV7());
        permission.setName(name);
        permission.setDescription("Permission for persistence specification test");
        return permission;
    }

    private EventEntity event(String title, PermissionEntity requiredPermission) {
        Instant begin = Instant.now().plusSeconds(7200);

        EventEntity event = new EventEntity();
        event.setId(UUIDGenerator.generateUUIDV7());
        event.setTitle(title + " " + UUID.randomUUID());
        event.setDescription("Specification persistence event");
        event.setRequiredPermission(requiredPermission);
        event.setType(EventType.GENERIC);
        event.setStatus(EventStatus.SCHEDULED);
        event.setBeginDate(begin);
        event.setEndDate(begin.plusSeconds(3600));
        return event;
    }

    private String uniqueEmail() {
        return "it-" + UUID.randomUUID() + "@example.com";
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
