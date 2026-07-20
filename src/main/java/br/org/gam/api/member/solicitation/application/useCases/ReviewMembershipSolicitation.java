package br.org.gam.api.member.solicitation.application.useCases;

import br.org.gam.api.account.application.AccountEntityLoader;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.member.application.MemberEntityLoader;
import br.org.gam.api.member.application.MemberRoleProjection;
import br.org.gam.api.member.application.useCases.registerMember.RegisterMember;
import br.org.gam.api.member.application.useCases.registerMember.RegisterMemberDTO;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.member.persistence.MemberRepository;
import br.org.gam.api.member.solicitation.application.MembershipSolicitationMapper;
import br.org.gam.api.member.solicitation.application.MembershipSolicitationRDTO;
import br.org.gam.api.member.solicitation.domain.MembershipSolicitationStatus;
import br.org.gam.api.member.solicitation.persistence.MembershipSolicitationEntity;
import br.org.gam.api.member.solicitation.persistence.MembershipSolicitationRepository;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.exception.ConflictException;
import br.org.gam.api.shared.exception.NotFoundException;
import br.org.gam.api.shared.validation.RequiredReason;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;

@Service
public class ReviewMembershipSolicitation {
    private final MembershipSolicitationRepository solicitationRepo;
    private final MembershipSolicitationMapper mapper;
    private final AccountEntityLoader accountEntityLoader;
    private final MemberRepository memberRepo;
    private final RegisterMember registerMember;
    private final MemberEntityLoader memberEntityLoader;
    private final MemberRoleProjection roleProjection;
    private final AuditorAware<UUID> auditorAware;
    private final ActivityEvents activityEvents;

    public ReviewMembershipSolicitation(MembershipSolicitationRepository solicitationRepo,
                                        MembershipSolicitationMapper mapper,
                                        AccountEntityLoader accountEntityLoader, MemberRepository memberRepo,
                                        RegisterMember registerMember, MemberEntityLoader memberEntityLoader,
                                        MemberRoleProjection roleProjection, AuditorAware<UUID> auditorAware,
                                        ActivityEvents activityEvents) {
        this.solicitationRepo = solicitationRepo;
        this.mapper = mapper;
        this.accountEntityLoader = accountEntityLoader;
        this.memberRepo = memberRepo;
        this.registerMember = registerMember;
        this.memberEntityLoader = memberEntityLoader;
        this.roleProjection = roleProjection;
        this.auditorAware = auditorAware;
        this.activityEvents = activityEvents;
    }

    @Transactional
    public MembershipSolicitationRDTO approve(UUID solicitationId, String submittedReason) {
        String reason = normalizeReason(submittedReason);
        MembershipSolicitationEntity solicitation = pendingForUpdate(solicitationId);
        AccountEntity applicant = accountEntityLoader.requiredByIdForUpdate(solicitation.getAccount().getId());
        Member.validateEligibility(solicitation.getBirthDate(), LocalDate.now());
        if (memberRepo.existsByAccountId(applicant.getId())) {
            throw ConflictException.resource("Account", applicant.getId(), "This Account already has a lifetime Member.");
        }
        roleProjection.assertPreMember(applicant.getId());

        UUID memberId = registerMember.register(new RegisterMemberDTO(
                applicant.getId(), solicitation.getName().firstName(), solicitation.getName().surname(),
                solicitation.getBirthDate(), solicitation.getPhoneNumber(), reason
        )).id();
        MemberEntity member = memberEntityLoader.requiredById(memberId);
        MemberRoleProjection.RoleChange roles = roleProjection.synchronizeActive(applicant.getId());

        decide(solicitation, MembershipSolicitationStatus.APPROVED, reason);
        solicitation.setMember(member);
        MembershipSolicitationEntity saved = solicitationRepo.saveAndFlush(solicitation);
        activityEvents.membershipSolicitationApproved(
                saved.getId(), applicant.getId(), memberId,
                roles.roleAddedId(), roles.roleRemovedId(), reason
        );
        return mapper.entityToRDTO(saved);
    }

    @Transactional
    public MembershipSolicitationRDTO reject(UUID solicitationId, String submittedReason) {
        String reason = normalizeReason(submittedReason);
        MembershipSolicitationEntity solicitation = pendingForUpdate(solicitationId);
        accountEntityLoader.requiredByIdForUpdate(solicitation.getAccount().getId());
        decide(solicitation, MembershipSolicitationStatus.REJECTED, reason);
        MembershipSolicitationEntity saved = solicitationRepo.saveAndFlush(solicitation);
        activityEvents.membershipSolicitationRejected(saved.getId(), saved.getAccount().getId(), reason);
        return mapper.entityToRDTO(saved);
    }

    private MembershipSolicitationEntity pendingForUpdate(UUID id) {
        MembershipSolicitationEntity solicitation = solicitationRepo.findByIdForUpdate(id)
                .orElseThrow(() -> NotFoundException.resource("MembershipSolicitation", id));
        if (solicitation.getStatus() != MembershipSolicitationStatus.PENDING) {
            throw ConflictException.resource("MembershipSolicitation", id, "Membership solicitation is already decided.");
        }
        return solicitation;
    }

    private void decide(MembershipSolicitationEntity solicitation, MembershipSolicitationStatus status, String reason) {
        UUID reviewerId = auditorAware.getCurrentAuditor()
                .orElseThrow(() -> new IllegalStateException("Authenticated Account is required."));
        solicitation.setStatus(status);
        solicitation.setReviewedBy(accountEntityLoader.requiredById(reviewerId));
        solicitation.setDecidedAt(Instant.now());
        solicitation.setReviewReason(reason);
    }

    private String normalizeReason(String reason) {
        return RequiredReason.normalize(reason, "Membership solicitation review requires an audit reason.");
    }
}
