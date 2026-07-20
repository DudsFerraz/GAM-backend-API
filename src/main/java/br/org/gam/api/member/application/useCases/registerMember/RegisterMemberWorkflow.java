package br.org.gam.api.member.application.useCases.registerMember;

import br.org.gam.api.member.application.MemberEntityLoader;
import br.org.gam.api.account.application.AccountEntityLoader;
import br.org.gam.api.member.application.MemberMapper;
import br.org.gam.api.member.application.MemberRDTO;
import br.org.gam.api.member.application.MemberRoleProjection;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.member.solicitation.domain.MembershipSolicitationStatus;
import br.org.gam.api.member.solicitation.persistence.MembershipSolicitationRepository;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.exception.ConflictException;
import br.org.gam.api.shared.validation.RequiredReason;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RegisterMemberWorkflow {
    private final RegisterMember registerMember;
    private final MemberEntityLoader memberEntityLoader;
    private final MemberMapper memberMapper;
    private final MembershipSolicitationRepository solicitationRepo;
    private final MemberRoleProjection roleProjection;
    private final ActivityEvents activityEvents;
    private final AccountEntityLoader accountEntityLoader;

    public RegisterMemberWorkflow(RegisterMember registerMember, MemberEntityLoader memberEntityLoader,
                                  MemberMapper memberMapper, MembershipSolicitationRepository solicitationRepo,
                                  MemberRoleProjection roleProjection, ActivityEvents activityEvents,
                                  AccountEntityLoader accountEntityLoader) {
        this.registerMember = registerMember;
        this.memberEntityLoader = memberEntityLoader;
        this.memberMapper = memberMapper;
        this.solicitationRepo = solicitationRepo;
        this.roleProjection = roleProjection;
        this.activityEvents = activityEvents;
        this.accountEntityLoader = accountEntityLoader;
    }

    @Transactional
    public MemberRDTO register(RegisterMemberDTO dto) {
        String reason = RequiredReason.normalize(dto.reason(), "Member registration requires an audit reason.");
        boolean solicitationWasPending = hasPendingSolicitation(dto.accountId());
        accountEntityLoader.requiredByIdForUpdate(dto.accountId());
        if (solicitationWasPending || hasPendingSolicitation(dto.accountId())) {
            throw ConflictException.resource(
                    "MembershipSolicitation", dto.accountId(),
                    "A pending membership solicitation already exists for this Account."
            );
        }
        roleProjection.assertPreMember(dto.accountId());

        UUID memberId = registerMember.register(dto).id();
        MemberEntity member = memberEntityLoader.requiredById(memberId);
        MemberRoleProjection.RoleChange roles = roleProjection.synchronizeActive(member.getAccount().getId());
        activityEvents.memberRegistered(
                member.getId(), member.getAccount().getId(), roles.roleAddedId(), roles.roleRemovedId(), reason
        );
        return memberMapper.entityToRDTO(member);
    }

    private boolean hasPendingSolicitation(UUID accountId) {
        return solicitationRepo.existsByAccount_IdAndStatus(accountId, MembershipSolicitationStatus.PENDING);
    }
}
