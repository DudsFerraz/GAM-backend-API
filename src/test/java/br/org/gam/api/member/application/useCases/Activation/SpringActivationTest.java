package br.org.gam.api.member.application.useCases.Activation;

import br.org.gam.api.account.domain.Account;
import br.org.gam.api.account.domain.MyEmail;
import br.org.gam.api.member.application.MemberMapper;
import br.org.gam.api.member.application.useCases.GetMemberInstance.GetMemberInstance;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.member.persistence.MemberRepository;
import br.org.gam.api.rbac.AccountRole.application.useCases.AddAccountRole.AddAccountRole;
import br.org.gam.api.rbac.AccountRole.application.useCases.DropAccountRole.DropAccountRole;
import br.org.gam.api.rbac.AccountRole.domain.AccountRole;
import br.org.gam.api.shared.domain.Name;
import br.org.gam.api.shared.phonenumber.MyPhoneNumber;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Member Activation Use Case")
class SpringActivationTest {

    @Mock
    private MemberRepository memberRepo;

    @Mock
    private GetMemberInstance getMemberInstance;

    @Mock
    private MemberMapper memberMapper;

    @Mock
    private AddAccountRole addAccountRole;

    @Mock
    private DropAccountRole dropAccountRole;

    @InjectMocks
    private SpringActivation activation;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - activate member -> active status and member role")
        void activateMemberShouldSetActiveStatusAndMemberRole() {
            UUID memberId = UUID.randomUUID();
            Account account = account();
            Member member = member(account);
            MemberEntity mappedEntity = new MemberEntity();

            when(getMemberInstance.domainById(memberId)).thenReturn(member);
            when(memberMapper.domainToEntity(any(Member.class))).thenReturn(mappedEntity);

            activation.activate(memberId);

            ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
            verify(memberMapper).domainToEntity(memberCaptor.capture());
            assertThat(memberCaptor.getValue().getStatus()).isEqualTo(MemberStatus.ACTIVE);
            verify(addAccountRole).byRoleName("MEMBER", account.getId());
            verify(dropAccountRole).byRoleName("VISITOR", account.getId());
            verify(memberRepo).save(mappedEntity);
        }

        @Test
        @DisplayName("EP - deactivate member -> inactive status and visitor role")
        void deactivateMemberShouldSetInactiveStatusAndVisitorRole() {
            UUID memberId = UUID.randomUUID();
            Account account = account();
            Member member = member(account);
            member.activate();
            MemberEntity mappedEntity = new MemberEntity();

            when(getMemberInstance.domainById(memberId)).thenReturn(member);
            when(memberMapper.domainToEntity(any(Member.class))).thenReturn(mappedEntity);

            activation.deactivate(memberId);

            ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
            verify(memberMapper).domainToEntity(memberCaptor.capture());
            assertThat(memberCaptor.getValue().getStatus()).isEqualTo(MemberStatus.INACTIVE);
            verify(addAccountRole).byRoleName("VISITOR", account.getId());
            verify(dropAccountRole).byRoleName("MEMBER", account.getId());
            verify(memberRepo).save(mappedEntity);
        }
    }

    private static Member member(Account account) {
        return Member.register(
                account,
                new Name("Ana", "Silva"),
                LocalDate.now().minusYears(20),
                MyPhoneNumber.parse("+5519998877665", "BR")
        );
    }

    private static Account account() {
        return Account.register(MyEmail.of("member@example.com"), "encoded-password", "Member Account");
    }
}
