package br.org.gam.api.member.solicitation.application;

import br.org.gam.api.account.application.AccountMapper;
import br.org.gam.api.account.application.AccountSummaryRDTO;
import br.org.gam.api.member.solicitation.persistence.MembershipSolicitationEntity;
import br.org.gam.api.shared.domain.GamEmail;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring", uses = AccountMapper.class)
public interface MembershipSolicitationMapper {

    // =====================================================================================
    // Persistence -> RDTO
    // =====================================================================================

    @Mapping(target = "firstName", source = "name.firstName")
    @Mapping(target = "surname", source = "name.surname")
    @Mapping(target = "submittedAt", source = "createdAt")
    @Mapping(target = "memberId", source = ".", qualifiedByName = "approvedMemberId")
    @Mapping(target = "account", source = ".", qualifiedByName = "applicantAccountSummary")
    @Mapping(target = "reviewedBy", source = ".", qualifiedByName = "reviewerAccountSummary")
    MembershipSolicitationRDTO entityToRDTO(MembershipSolicitationEntity entity);

    // =====================================================================================
    // Helpers
    // =====================================================================================

    @Named("applicantAccountSummary")
    default AccountSummaryRDTO applicantAccountSummary(MembershipSolicitationEntity entity) {
        if (entity.getApplicantAccountId() != null
                && entity.getApplicantEmail() != null
                && entity.getApplicantDisplayName() != null) {
            return new AccountSummaryRDTO(
                    entity.getApplicantAccountId(),
                    GamEmail.of(entity.getApplicantEmail()),
                    entity.getApplicantDisplayName()
            );
        }
        return new AccountSummaryRDTO(
                entity.getAccount().getId(),
                entity.getAccount().getEmail(),
                entity.getAccount().getDisplayName()
        );
    }

    @Named("reviewerAccountSummary")
    default AccountSummaryRDTO reviewerAccountSummary(MembershipSolicitationEntity entity) {
        if (entity.getReviewerAccountId() != null
                && entity.getReviewerEmail() != null
                && entity.getReviewerDisplayName() != null) {
            return new AccountSummaryRDTO(
                    entity.getReviewerAccountId(),
                    GamEmail.of(entity.getReviewerEmail()),
                    entity.getReviewerDisplayName()
            );
        }
        if (entity.getReviewedBy() == null) {
            return null;
        }
        return new AccountSummaryRDTO(
                entity.getReviewedBy().getId(),
                entity.getReviewedBy().getEmail(),
                entity.getReviewedBy().getDisplayName()
        );
    }

    @Named("approvedMemberId")
    default UUID approvedMemberId(MembershipSolicitationEntity entity) {
        if (entity.getApprovedMemberId() != null) {
            return entity.getApprovedMemberId();
        }
        return entity.getMember() == null ? null : entity.getMember().getId();
    }
}
