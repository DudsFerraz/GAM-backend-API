package br.org.gam.api.member.application;

import br.org.gam.api.account.domain.Account;
import br.org.gam.api.account.domain.MyEmail;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.member.application.useCases.GetMemberInstance.GetMemberInstance;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.rbac.Permission.domain.Permission;
import br.org.gam.api.rbac.Permission.domain.PermissionEnum;
import br.org.gam.api.security.application.AccountDetails;
import br.org.gam.api.security.SecurityUtils;
import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Member Security Helper")
class MemberSecurityTest {

    @Mock
    private GetMemberInstance getMemberInstance;

    @Mock
    private SecurityUtils securityUtils;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @StructuralTest
    @DisplayName("Structural")
    class Structural {

        @Test
        @DisplayName("active member -> visible")
        void activeMemberShouldBeVisible() {
            MemberSecurity memberSecurity = new MemberSecurity(getMemberInstance, securityUtils);
            MemberEntity member = member(MemberStatus.ACTIVE, UUID.randomUUID());

            assertThat(memberSecurity.canGetMember(member)).isTrue();
            verifyNoInteractions(securityUtils);
        }

        @Test
        @DisplayName("non active member with authority -> visible")
        void nonActiveMemberWithAuthorityShouldBeVisible() {
            MemberSecurity memberSecurity = new MemberSecurity(getMemberInstance, securityUtils);
            MemberEntity member = member(MemberStatus.PENDENT, UUID.randomUUID());

            when(securityUtils.getLoggedUserAuthorities()).thenReturn(Set.of(PermissionEnum.MEMBER_GET_NON_ACTIVE.name()));

            assertThat(memberSecurity.canGetMember(member)).isTrue();
        }

        @Test
        @DisplayName("non active member without authority -> hidden")
        void nonActiveMemberWithoutAuthorityShouldBeHidden() {
            MemberSecurity memberSecurity = new MemberSecurity(getMemberInstance, securityUtils);
            MemberEntity member = member(MemberStatus.INACTIVE, UUID.randomUUID());

            when(securityUtils.getLoggedUserAuthorities()).thenReturn(Set.of("MEMBER_GET"));

            assertThat(memberSecurity.canGetMember(member)).isFalse();
        }

        @Test
        @DisplayName("missing authentication for member presences -> hidden")
        void missingAuthenticationForMemberPresencesShouldBeHidden() {
            MemberSecurity memberSecurity = new MemberSecurity(getMemberInstance, securityUtils);

            assertThat(memberSecurity.canGetMemberPresences(UUID.randomUUID())).isFalse();
            verifyNoInteractions(getMemberInstance);
        }

        @Test
        @DisplayName("presence search authority -> visible")
        void presenceSearchAuthorityShouldBeVisible() {
            MemberSecurity memberSecurity = new MemberSecurity(getMemberInstance, securityUtils);
            UUID loggedAccountId = UUID.randomUUID();
            AccountDetails accountDetails = accountDetails(loggedAccountId, List.of(new SimpleGrantedAuthority(PermissionEnum.PRESENCES_SEARCH.name())));
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(accountDetails, "password", accountDetails.getAuthorities()));

            assertThat(memberSecurity.canGetMemberPresences(UUID.randomUUID())).isTrue();
            verifyNoInteractions(getMemberInstance);
        }

        @Test
        @DisplayName("target member account matches logged account -> visible")
        void targetMemberAccountMatchesLoggedAccountShouldBeVisible() {
            MemberSecurity memberSecurity = new MemberSecurity(getMemberInstance, securityUtils);
            UUID loggedAccountId = UUID.randomUUID();
            UUID targetMemberId = UUID.randomUUID();
            AccountDetails accountDetails = accountDetails(loggedAccountId, List.of());
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(accountDetails, "password", accountDetails.getAuthorities()));
            when(getMemberInstance.entityById(targetMemberId)).thenReturn(member(MemberStatus.ACTIVE, loggedAccountId));

            assertThat(memberSecurity.canGetMemberPresences(targetMemberId)).isTrue();
        }

        @Test
        @DisplayName("target member account differs from logged account -> hidden")
        void targetMemberAccountDiffersFromLoggedAccountShouldBeHidden() {
            MemberSecurity memberSecurity = new MemberSecurity(getMemberInstance, securityUtils);
            UUID targetMemberId = UUID.randomUUID();
            AccountDetails accountDetails = accountDetails(UUID.randomUUID(), List.of());
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(accountDetails, "password", accountDetails.getAuthorities()));
            when(getMemberInstance.entityById(targetMemberId)).thenReturn(member(MemberStatus.ACTIVE, UUID.randomUUID()));

            assertThat(memberSecurity.canGetMemberPresences(targetMemberId)).isFalse();
        }
    }

    private static MemberEntity member(MemberStatus status, UUID accountId) {
        AccountEntity account = account(accountId);
        MemberEntity member = new MemberEntity();
        member.setStatus(status);
        member.setAccount(account);
        return member;
    }

    private static AccountDetails accountDetails(UUID accountId, List<SimpleGrantedAuthority> authorities) {
        return new AccountDetails(account(accountId), authorities);
    }

    private static AccountEntity account(UUID accountId) {
        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setEmail(MyEmail.of(accountId + "@example.com"));
        account.setPasswordHash("encoded-password");
        account.setDisplayName("Account");
        return account;
    }
}
