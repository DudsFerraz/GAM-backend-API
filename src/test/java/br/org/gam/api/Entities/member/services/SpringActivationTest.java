package br.org.gam.api.Entities.member.services;

import br.org.gam.api.Entities.RBAC.accountRole.services.addAccountRole.AddAccountRole;
import br.org.gam.api.Entities.RBAC.accountRole.services.dropAccountRole.DropAccountRole;
import br.org.gam.api.Entities.account.Account;
import br.org.gam.api.Entities.account.myEmail.MyEmail;
import br.org.gam.api.Entities.member.Member;
import br.org.gam.api.Entities.member.MemberMapper;
import br.org.gam.api.Entities.member.MemberStatus;
import br.org.gam.api.Entities.member.persistence.MemberEntity;
import br.org.gam.api.Entities.member.persistence.MemberRepository;
import br.org.gam.api.Entities.member.services.activation.SpringActivation;
import br.org.gam.api.Entities.member.services.getMemberInstance.GetMemberInstance;
import br.org.gam.api.common.Name;
import br.org.gam.api.common.myPhoneNumber.MyPhoneNumber;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

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
