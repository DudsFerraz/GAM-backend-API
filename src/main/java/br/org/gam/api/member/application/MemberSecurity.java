package br.org.gam.api.member.application;

import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.security.application.AccountDetails;
import br.org.gam.api.security.SecurityUtils;
import br.org.gam.api.shared.exception.NotFoundException;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("memberSecurity")
public class MemberSecurity {
    private final MemberEntityLoader getMemberInstance;
    private final SecurityUtils securityUtils;
    public MemberSecurity(MemberEntityLoader getMemberInstance, SecurityUtils securityUtils) {
        this.getMemberInstance = getMemberInstance;
        this.securityUtils = securityUtils;
    }

    @Transactional(readOnly = true)
    public boolean canGetMemberById(UUID targetMemberId) {
        MemberEntity member = getMemberInstance.requiredById(targetMemberId);
        if (isLinkedToLoggedAccount(member)) return true;

        Set<String> userAuthorities = securityUtils.getLoggedUserAuthorities();
        if (!userAuthorities.contains(PermissionEnum.MEMBER_GET.getCode())) return false;

        requireVisibleStatus(member, userAuthorities);

        return true;
    }

    @Transactional(readOnly = true)
    public boolean canGetMemberPresences(UUID targetMemberId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        MemberEntity member = getMemberInstance.requiredById(targetMemberId);
        if (isLinkedToLoggedAccount(member)) return true;

        Set<String> userAuthorities = securityUtils.getLoggedUserAuthorities();
        requireVisibleStatus(member, userAuthorities);

        return userAuthorities.contains(PermissionEnum.PRESENCES_SEARCH.getCode());
    }

    public boolean canGetMember(MemberEntity memberEntity) {
        if (isLinkedToLoggedAccount(memberEntity)) return true;
        if (memberEntity.getStatus() == MemberStatus.ACTIVE) return true;

        Set<String> userAuthorities = securityUtils.getLoggedUserAuthorities();

        return userAuthorities.contains(PermissionEnum.MEMBER_GET_NON_ACTIVE.getCode());
    }

    private boolean isLinkedToLoggedAccount(MemberEntity member) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountDetails accountDetails)) {
            return false;
        }

        return member.getAccount().getId().equals(accountDetails.getId());
    }

    private static void requireVisibleStatus(MemberEntity member, Set<String> userAuthorities) {
        if (member.getStatus() == MemberStatus.INACTIVE
                && !userAuthorities.contains(PermissionEnum.MEMBER_GET_NON_ACTIVE.getCode())) {
            throw NotFoundException.resource("Member", member.getId());
        }
    }
}
