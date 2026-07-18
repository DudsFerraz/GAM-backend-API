package br.org.gam.api.member.application;

import br.org.gam.api.account.domain.Account;
import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.member.application.MemberEntityLoader;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.security.application.AccountDetails;
import br.org.gam.api.security.SecurityUtils;
import br.org.gam.api.shared.exception.NotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Member Security Helper")
class MemberSecurityTest {

    @Mock
    private MemberEntityLoader getMemberInstance;

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
        @DisplayName("REQ-MEMBER-013 - linked inactive Member without general read permissions -> visible")
        void linkedInactiveMemberShouldBeVisibleById() {
            MemberSecurity memberSecurity = new MemberSecurity(getMemberInstance, securityUtils);
            UUID loggedAccountId = UUID.randomUUID();
            UUID targetMemberId = UUID.randomUUID();
            AccountDetails accountDetails = accountDetails(loggedAccountId, List.of());
            SecurityContextHolder.getContext().setAuthentication(
                    new TestingAuthenticationToken(accountDetails, "password", accountDetails.getAuthorities())
            );
            when(getMemberInstance.requiredById(targetMemberId))
                    .thenReturn(member(targetMemberId, MemberStatus.INACTIVE, loggedAccountId));

            assertThat(memberSecurity.canGetMemberById(targetMemberId)).isTrue();
            verifyNoInteractions(securityUtils);
        }

        @Test
        @DisplayName("REQ-MEMBER-013 - another active Member with MEMBER_GET -> visible")
        void activeMemberWithMemberGetShouldBeVisibleById() {
            MemberSecurity memberSecurity = new MemberSecurity(getMemberInstance, securityUtils);
            UUID targetMemberId = UUID.randomUUID();
            authenticate(UUID.randomUUID(), PermissionEnum.MEMBER_GET.getCode());
            when(getMemberInstance.requiredById(targetMemberId))
                    .thenReturn(member(targetMemberId, MemberStatus.ACTIVE, UUID.randomUUID()));
            when(securityUtils.getLoggedUserAuthorities()).thenReturn(Set.of(PermissionEnum.MEMBER_GET.getCode()));

            assertThat(memberSecurity.canGetMemberById(targetMemberId)).isTrue();
        }

        @Test
        @DisplayName("REQ-MEMBER-013 - MEMBER_GET_NON_ACTIVE without MEMBER_GET -> direct lookup denied")
        void nonActiveVisibilityAloneShouldNotGrantDirectLookup() {
            MemberSecurity memberSecurity = new MemberSecurity(getMemberInstance, securityUtils);
            UUID targetMemberId = UUID.randomUUID();
            authenticate(UUID.randomUUID(), PermissionEnum.MEMBER_GET_NON_ACTIVE.getCode());
            when(getMemberInstance.requiredById(targetMemberId))
                    .thenReturn(member(targetMemberId, MemberStatus.ACTIVE, UUID.randomUUID()));
            when(securityUtils.getLoggedUserAuthorities())
                    .thenReturn(Set.of(PermissionEnum.MEMBER_GET_NON_ACTIVE.getCode()));

            assertThat(memberSecurity.canGetMemberById(targetMemberId)).isFalse();
        }

        @Test
        @DisplayName("REQ-MEMBER-013 - another inactive Member without read permissions -> direct lookup denied")
        void inactiveMemberWithoutReadPermissionsShouldReturnCapabilityDenial() {
            MemberSecurity memberSecurity = new MemberSecurity(getMemberInstance, securityUtils);
            UUID targetMemberId = UUID.randomUUID();
            authenticate(UUID.randomUUID());
            when(getMemberInstance.requiredById(targetMemberId))
                    .thenReturn(member(targetMemberId, MemberStatus.INACTIVE, UUID.randomUUID()));
            when(securityUtils.getLoggedUserAuthorities()).thenReturn(Set.of());

            assertThat(memberSecurity.canGetMemberById(targetMemberId)).isFalse();
        }

        @Test
        @DisplayName("REQ-MEMBER-013 - another inactive Member without non-active visibility -> not found")
        void inactiveMemberWithoutNonActiveVisibilityShouldBeHiddenById() {
            MemberSecurity memberSecurity = new MemberSecurity(getMemberInstance, securityUtils);
            UUID targetMemberId = UUID.randomUUID();
            authenticate(UUID.randomUUID(), PermissionEnum.MEMBER_GET.getCode());
            when(getMemberInstance.requiredById(targetMemberId))
                    .thenReturn(member(targetMemberId, MemberStatus.INACTIVE, UUID.randomUUID()));
            when(securityUtils.getLoggedUserAuthorities()).thenReturn(Set.of(PermissionEnum.MEMBER_GET.getCode()));

            assertThatThrownBy(() -> memberSecurity.canGetMemberById(targetMemberId))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("REQ-MEMBER-013 - another inactive Member with both read permissions -> visible")
        void inactiveMemberWithCompleteReadPermissionsShouldBeVisibleById() {
            MemberSecurity memberSecurity = new MemberSecurity(getMemberInstance, securityUtils);
            UUID targetMemberId = UUID.randomUUID();
            authenticate(
                    UUID.randomUUID(),
                    PermissionEnum.MEMBER_GET.getCode(),
                    PermissionEnum.MEMBER_GET_NON_ACTIVE.getCode()
            );
            when(getMemberInstance.requiredById(targetMemberId))
                    .thenReturn(member(targetMemberId, MemberStatus.INACTIVE, UUID.randomUUID()));
            when(securityUtils.getLoggedUserAuthorities()).thenReturn(Set.of(
                    PermissionEnum.MEMBER_GET.getCode(),
                    PermissionEnum.MEMBER_GET_NON_ACTIVE.getCode()
            ));

            assertThat(memberSecurity.canGetMemberById(targetMemberId)).isTrue();
        }

        @Test
        @DisplayName("REQ-MEMBER-013 - missing Member direct lookup -> not found")
        void missingMemberLookupShouldRemainNotFound() {
            MemberSecurity memberSecurity = new MemberSecurity(getMemberInstance, securityUtils);
            UUID targetMemberId = UUID.randomUUID();
            authenticate(UUID.randomUUID(), PermissionEnum.MEMBER_GET.getCode());
            when(getMemberInstance.requiredById(targetMemberId))
                    .thenThrow(NotFoundException.resource("Member", targetMemberId));

            assertThatThrownBy(() -> memberSecurity.canGetMemberById(targetMemberId))
                    .isInstanceOf(NotFoundException.class);
            verifyNoInteractions(securityUtils);
        }

        @Test
        @DisplayName("REQ-MEMBER-015 - missing authentication for Member presences -> hidden")
        void missingAuthenticationForMemberPresencesShouldBeHidden() {
            MemberSecurity memberSecurity = new MemberSecurity(getMemberInstance, securityUtils);

            assertThat(memberSecurity.canGetMemberPresences(UUID.randomUUID())).isFalse();
            verifyNoInteractions(getMemberInstance);
        }

        @Test
        @DisplayName("REQ-MEMBER-015 - linked inactive Member without general permissions -> presence history visible")
        void linkedInactiveMemberPresenceHistoryShouldBeVisible() {
            MemberSecurity memberSecurity = new MemberSecurity(getMemberInstance, securityUtils);
            UUID loggedAccountId = UUID.randomUUID();
            UUID targetMemberId = UUID.randomUUID();
            AccountDetails accountDetails = accountDetails(loggedAccountId, List.of());
            SecurityContextHolder.getContext().setAuthentication(
                    new TestingAuthenticationToken(accountDetails, "password", accountDetails.getAuthorities())
            );
            when(getMemberInstance.requiredById(targetMemberId))
                    .thenReturn(member(targetMemberId, MemberStatus.INACTIVE, loggedAccountId));

            assertThat(memberSecurity.canGetMemberPresences(targetMemberId)).isTrue();
            verifyNoInteractions(securityUtils);
        }

        @Test
        @DisplayName("REQ-MEMBER-015 - PRESENCES_SEARCH with active resolved Member -> presence history visible")
        void presenceSearchShouldExposeResolvedActiveMemberHistory() {
            MemberSecurity memberSecurity = new MemberSecurity(getMemberInstance, securityUtils);
            UUID targetMemberId = UUID.randomUUID();
            authenticate(UUID.randomUUID(), PermissionEnum.PRESENCES_SEARCH.getCode());
            when(getMemberInstance.requiredById(targetMemberId))
                    .thenReturn(member(targetMemberId, MemberStatus.ACTIVE, UUID.randomUUID()));
            when(securityUtils.getLoggedUserAuthorities())
                    .thenReturn(Set.of(PermissionEnum.PRESENCES_SEARCH.getCode()));

            assertThat(memberSecurity.canGetMemberPresences(targetMemberId)).isTrue();
        }

        @Test
        @DisplayName("REQ-MEMBER-015 - PRESENCES_SEARCH without inactive visibility -> not found")
        void presenceSearchShouldNotBypassInactiveMemberVisibility() {
            MemberSecurity memberSecurity = new MemberSecurity(getMemberInstance, securityUtils);
            UUID targetMemberId = UUID.randomUUID();
            authenticate(UUID.randomUUID(), PermissionEnum.PRESENCES_SEARCH.getCode());
            when(getMemberInstance.requiredById(targetMemberId))
                    .thenReturn(member(targetMemberId, MemberStatus.INACTIVE, UUID.randomUUID()));
            when(securityUtils.getLoggedUserAuthorities())
                    .thenReturn(Set.of(PermissionEnum.PRESENCES_SEARCH.getCode()));

            assertThatThrownBy(() -> memberSecurity.canGetMemberPresences(targetMemberId))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("REQ-MEMBER-015 - complete inactive presence permissions -> presence history visible")
        void completeInactivePresencePermissionsShouldExposeHistory() {
            MemberSecurity memberSecurity = new MemberSecurity(getMemberInstance, securityUtils);
            UUID targetMemberId = UUID.randomUUID();
            authenticate(
                    UUID.randomUUID(),
                    PermissionEnum.PRESENCES_SEARCH.getCode(),
                    PermissionEnum.MEMBER_GET_NON_ACTIVE.getCode()
            );
            when(getMemberInstance.requiredById(targetMemberId))
                    .thenReturn(member(targetMemberId, MemberStatus.INACTIVE, UUID.randomUUID()));
            when(securityUtils.getLoggedUserAuthorities()).thenReturn(Set.of(
                    PermissionEnum.PRESENCES_SEARCH.getCode(),
                    PermissionEnum.MEMBER_GET_NON_ACTIVE.getCode()
            ));

            assertThat(memberSecurity.canGetMemberPresences(targetMemberId)).isTrue();
        }

        @Test
        @DisplayName("REQ-MEMBER-015 - missing Member with PRESENCES_SEARCH -> not found")
        void presenceSearchShouldResolveTheParentMember() {
            MemberSecurity memberSecurity = new MemberSecurity(getMemberInstance, securityUtils);
            UUID targetMemberId = UUID.randomUUID();
            authenticate(UUID.randomUUID(), PermissionEnum.PRESENCES_SEARCH.getCode());
            when(getMemberInstance.requiredById(targetMemberId))
                    .thenThrow(NotFoundException.resource("Member", targetMemberId));

            assertThatThrownBy(() -> memberSecurity.canGetMemberPresences(targetMemberId))
                    .isInstanceOf(NotFoundException.class);
            verifyNoInteractions(securityUtils);
        }

        @Test
        @DisplayName("REQ-MEMBER-015 - another active Member without PRESENCES_SEARCH -> hidden")
        void targetMemberAccountDiffersFromLoggedAccountShouldBeHidden() {
            MemberSecurity memberSecurity = new MemberSecurity(getMemberInstance, securityUtils);
            UUID targetMemberId = UUID.randomUUID();
            AccountDetails accountDetails = accountDetails(UUID.randomUUID(), List.of());
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(accountDetails, "password", accountDetails.getAuthorities()));
            when(getMemberInstance.requiredById(targetMemberId)).thenReturn(member(MemberStatus.ACTIVE, UUID.randomUUID()));

            assertThat(memberSecurity.canGetMemberPresences(targetMemberId)).isFalse();
        }
    }

    private static MemberEntity member(MemberStatus status, UUID accountId) {
        return member(UUID.randomUUID(), status, accountId);
    }

    private static MemberEntity member(UUID memberId, MemberStatus status, UUID accountId) {
        AccountEntity account = account(accountId);
        MemberEntity member = new MemberEntity();
        member.setId(memberId);
        member.setStatus(status);
        member.setAccount(account);
        return member;
    }

    private static void authenticate(UUID accountId, String... permissionCodes) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(permissionCodes)
                .map(SimpleGrantedAuthority::new)
                .toList();
        AccountDetails accountDetails = accountDetails(accountId, authorities);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(accountDetails, "password", accountDetails.getAuthorities())
        );
    }

    private static AccountDetails accountDetails(UUID accountId, List<SimpleGrantedAuthority> authorities) {
        return new AccountDetails(account(accountId), authorities);
    }

    private static AccountEntity account(UUID accountId) {
        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setEmail(GamEmail.of(accountId + "@example.com"));
        account.setPasswordHash("encoded-password");
        account.setDisplayName("Account");
        return account;
    }
}
