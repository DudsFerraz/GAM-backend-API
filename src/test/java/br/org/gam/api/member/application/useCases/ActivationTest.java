package br.org.gam.api.member.application.useCases;

import br.org.gam.api.account.domain.Account;
import br.org.gam.api.account.application.AccountEntityLoader;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.member.application.CoordinatorSafetyPolicy;
import br.org.gam.api.member.application.MemberEntityLoader;
import br.org.gam.api.member.application.MemberMapper;
import br.org.gam.api.member.application.MemberRoleProjection;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.member.persistence.MemberRepository;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.domain.GamName;
import br.org.gam.api.shared.exception.InvalidCommandException;
import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Member Activation Use Case")
class ActivationTest {

    @Mock
    private MemberRepository memberRepo;

    @Mock
    private MemberEntityLoader memberEntityLoader;

    @Mock
    private AccountEntityLoader accountEntityLoader;

    @Mock
    private CoordinatorSafetyPolicy coordinatorSafetyPolicy;

    @Mock
    private MemberMapper memberMapper;

    @Mock
    private MemberRoleProjection memberRoleProjection;

    @Mock
    private ActivityEvents activityEvents;

    @InjectMocks
    private Activation activation;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("REQ-MEMBER-004 through REQ-MEMBER-007 and REQ-MEMBER-012 - active Member deactivates -> inactive status, visitor role, and one event")
        void deactivateMemberShouldSetInactiveStatusAndVisitorRole() {
            UUID memberId = UUID.randomUUID();
            Account account = account();
            Member member = member(account);
            member.activate();
            MemberEntity lockedEntity = memberEntity(account.getId());
            MemberEntity mappedEntity = new MemberEntity();

            when(memberEntityLoader.requiredByIdForUpdate(memberId)).thenReturn(lockedEntity);
            when(memberMapper.entityToDomain(lockedEntity)).thenReturn(member);
            when(memberMapper.domainToEntity(any(Member.class))).thenReturn(mappedEntity);
            when(memberRoleProjection.synchronizeInactive(account.getId()))
                    .thenReturn(new MemberRoleProjection.RoleChange(UUID.randomUUID(), UUID.randomUUID()));

            activation.deactivate(memberId, "No longer participates");

            ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
            verify(memberMapper).domainToEntity(memberCaptor.capture());
            assertThat(memberCaptor.getValue().getStatus()).isEqualTo(MemberStatus.INACTIVE);
            verify(memberRoleProjection).synchronizeInactive(account.getId());
            verify(memberRepo).save(mappedEntity);
            verify(activityEvents).memberDeactivated(
                    memberId,
                    account.getId(),
                    MemberStatus.ACTIVE.name(),
                    MemberStatus.INACTIVE.name(),
                    "VISITOR",
                    "MEMBER",
                    "No longer participates"
            );
            org.mockito.Mockito.verifyNoMoreInteractions(activityEvents);
        }

        @Test
        @DisplayName("REQ-MEMBER-004 through REQ-MEMBER-007 and REQ-MEMBER-012 - inactive Member activates -> active status, Member role, normalized reason, persistence, and one event")
        void activateMemberShouldSetActiveStatusAndMemberRole() {
            UUID memberId = UUID.randomUUID();
            Account account = account();
            Member member = member(account);
            member.deactivate();
            MemberEntity lockedEntity = memberEntity(account.getId());
            MemberEntity mappedEntity = new MemberEntity();

            when(memberEntityLoader.requiredByIdForUpdate(memberId)).thenReturn(lockedEntity);
            when(memberMapper.entityToDomain(lockedEntity)).thenReturn(member);
            when(memberMapper.domainToEntity(any(Member.class))).thenReturn(mappedEntity);
            when(memberRoleProjection.synchronizeActive(account.getId()))
                    .thenReturn(new MemberRoleProjection.RoleChange(UUID.randomUUID(), UUID.randomUUID()));

            activation.activate(memberId, "  Returning to weekly activities  ");

            ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
            verify(memberMapper).domainToEntity(memberCaptor.capture());
            assertThat(memberCaptor.getValue().getStatus()).isEqualTo(MemberStatus.ACTIVE);
            verify(memberRoleProjection).synchronizeActive(account.getId());
            verify(memberRepo).save(mappedEntity);
            verify(activityEvents).memberActivated(
                    memberId,
                    account.getId(),
                    MemberStatus.INACTIVE.name(),
                    MemberStatus.ACTIVE.name(),
                    "MEMBER",
                    "VISITOR",
                    "Returning to weekly activities"
            );
            org.mockito.Mockito.verifyNoMoreInteractions(activityEvents);
        }

        @ParameterizedTest
        @MethodSource("invalidReasons")
        @DisplayName("REQ-MEMBER-006 - invalid deactivation reason -> rejected before loading or mutation")
        void invalidDeactivationReasonShouldReturnValidationError(String reason) {
            UUID memberId = UUID.randomUUID();
            if (reason != null && reason.length() > 2_000) {
                Member member = member(account());
                lenient().when(memberEntityLoader.requiredByIdForUpdate(memberId))
                        .thenReturn(memberEntity(member.getAccount().getId()));
                lenient().when(memberMapper.domainToEntity(any(Member.class))).thenReturn(new MemberEntity());
            }

            assertThatThrownBy(() -> activation.deactivate(memberId, reason))
                    .isInstanceOf(InvalidCommandException.class);

            verifyNoInteractions(memberEntityLoader, accountEntityLoader, memberRoleProjection, memberRepo, activityEvents);
        }

        private static Stream<String> invalidReasons() {
            return Stream.of(null, "", " \n\t ", "r".repeat(2_001));
        }
    }

    private static Member member(Account account) {
        return Member.register(
                account,
                new GamName("Ana", "Silva"),
                LocalDate.now().minusYears(20),
                GamPhoneNumber.fromString("+5519998877665")
        );
    }

    private static Account account() {
        return Account.register(GamEmail.of("member@example.com"), "encoded-password", "Member Account");
    }

    private static MemberEntity memberEntity(UUID accountId) {
        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        MemberEntity member = new MemberEntity();
        member.setAccount(account);
        return member;
    }
}
