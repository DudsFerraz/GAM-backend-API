package br.org.gam.api.event.missa.application.useCases;

import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.missa.domain.MissaResponsibility;
import br.org.gam.api.presence.persistence.PresenceEntity;
import br.org.gam.api.shared.activitylog.ActivityAction;
import br.org.gam.api.shared.validation.RequiredReason;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RemoveMissaMember {
    private final MissaUseCaseSupport support;

    RemoveMissaMember(MissaUseCaseSupport support) {
        this.support = support;
    }

    @Transactional
    public void remove(UUID id, MissaResponsibility responsibility, UUID memberId, String rawReason) {
        Instant evaluationInstant = support.clock.instant();
        MissaUseCaseSupport.MutationContext context = support.mutationContext(id, evaluationInstant);
        support.assertAssignmentMutable(id, responsibility, context.status(), evaluationInstant);
        String reason = support.normalizeOptionalReason(rawReason, "Invalid Missa assignment reason.");
        if (!support.assignmentExists(id, responsibility, memberId)) return;
        if (context.status() == EventStatus.COMPLETED && reason == null) {
            reason = RequiredReason.normalize(null, "Completed Missa assignment changes require an audit reason.");
        }
        PresenceEntity presence = support.presenceRepository.findByMember_IdAndEvent_Id(memberId, id)
                .orElseThrow(() -> new IllegalStateException(
                        "An open Missa assignment must retain its active Presence."
                ));
        support.jdbcTemplate.update(
                "DELETE FROM missa_assignments WHERE missa_id = ? AND responsibility = ? AND member_id = ?",
                id, responsibility.name(), memberId
        );
        support.activity(
                ActivityAction.MISSA_MEMBER_REMOVED, id, reason, "Member removed from Missa responsibility",
                Map.of("responsibility", responsibility.name(), "memberId", memberId, "presenceId", presence.getId())
        );
    }
}
