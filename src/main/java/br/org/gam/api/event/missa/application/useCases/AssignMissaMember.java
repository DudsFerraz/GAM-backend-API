package br.org.gam.api.event.missa.application.useCases;

import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.missa.application.MissaApiModels.MissaRDTO;
import br.org.gam.api.event.missa.domain.MissaResponsibility;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.presence.persistence.PresenceEntity;
import br.org.gam.api.shared.activitylog.ActivityAction;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import br.org.gam.api.shared.validation.RequiredReason;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignMissaMember {
    private final MissaUseCaseSupport support;

    AssignMissaMember(MissaUseCaseSupport support) {
        this.support = support;
    }

    @Transactional
    public MissaRDTO assign(UUID id, MissaResponsibility responsibility, UUID memberId, String rawReason) {
        Instant evaluationInstant = support.clock.instant();
        MissaUseCaseSupport.MutationContext context = support.mutationContext(id, evaluationInstant);
        support.assertAssignmentMutable(id, responsibility, context.status(), evaluationInstant);
        String reason = support.normalizeOptionalReason(rawReason, "Invalid Missa assignment reason.");

        UUID currentSingletonMember = responsibility.singleMember()
                ? support.singletonMemberId(id, responsibility) : null;
        if (responsibility.singleMember() && Objects.equals(currentSingletonMember, memberId)
                || !responsibility.singleMember() && support.assignmentExists(id, responsibility, memberId)) {
            return support.detail(context.missa(), evaluationInstant);
        }
        if (currentSingletonMember != null) {
            throw support.conflict(
                    "MISSA_RESPONSIBILITY_ALREADY_ASSIGNED",
                    "The Missa responsibility already has a Member.",
                    Map.of("missaId", id, "responsibility", responsibility.name(),
                            "currentMemberId", currentSingletonMember)
            );
        }
        if (context.status() == EventStatus.COMPLETED && reason == null) {
            reason = RequiredReason.normalize(null, "Completed Missa assignment changes require an audit reason.");
        }

        MemberStatus memberStatus = support.memberStatusForUpdate(memberId);
        if (memberStatus != MemberStatus.ACTIVE) {
            throw support.conflict(
                    "MISSA_MEMBER_NOT_ACTIVE",
                    "Only an active Member may receive a new Missa assignment.",
                    Map.of("missaId", id, "memberId", memberId, "status", memberStatus.name())
            );
        }
        MemberEntity member = support.entityManager.getReference(MemberEntity.class, memberId);
        PresenceEntity presence = support.presenceRepository.findByMember_IdAndEvent_Id(memberId, id).orElse(null);
        boolean presenceCreated = presence == null;
        if (presenceCreated) {
            presence = new PresenceEntity();
            presence.setId(UUIDGenerator.generateUUIDV7());
            presence.setMember(member);
            presence.setEvent(context.event());
            presence.setObservations(null);
            support.presenceRepository.saveAndFlush(presence);
        }

        support.jdbcTemplate.update(
                "INSERT INTO missa_assignments "
                        + "(missa_id, responsibility, member_id, created_at, created_by) VALUES (?, ?, ?, ?, ?)",
                id, responsibility.name(), memberId, Timestamp.from(evaluationInstant),
                support.auditorAware.getCurrentAuditor().orElse(null)
        );
        support.activity(
                ActivityAction.MISSA_MEMBER_ASSIGNED, id, reason, "Member assigned to Missa responsibility",
                Map.of("responsibility", responsibility.name(), "memberId", memberId,
                        "presenceId", presence.getId(), "presenceCreated", presenceCreated)
        );
        return support.detail(context.missa(), evaluationInstant);
    }
}
