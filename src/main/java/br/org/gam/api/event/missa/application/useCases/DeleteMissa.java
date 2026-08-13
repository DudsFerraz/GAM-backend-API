package br.org.gam.api.event.missa.application.useCases;

import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.shared.activitylog.ActivityAction;
import br.org.gam.api.shared.validation.RequiredReason;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteMissa {
    private final MissaUseCaseSupport support;

    DeleteMissa(MissaUseCaseSupport support) {
        this.support = support;
    }

    @Transactional
    public void delete(UUID id, String rawReason) {
        Instant evaluationInstant = support.clock.instant();
        String reason = RequiredReason.normalize(rawReason, "Missa deletion requires an audit reason.");
        MissaUseCaseSupport.MutationContext context = support.mutationContext(id, evaluationInstant);
        if (context.status() == EventStatus.LOCKED || context.status() == EventStatus.FINALIZED) {
            throw support.transitionConflict(id, context.status(), context.status());
        }
        long activePresenceCount = support.presenceRepository.countByEvent_Id(id);
        if (activePresenceCount > 0) {
            throw support.conflict(
                    "EVENT_HAS_PRESENCES", "The Missa has active Presence records.",
                    Map.of("eventId", id, "activePresenceCount", activePresenceCount)
            );
        }
        support.jdbcTemplate.update("DELETE FROM missa_assignments WHERE missa_id = ?", id);
        support.missaRepository.delete(context.missa());
        support.eventRepository.delete(context.event());
        support.activity(
                ActivityAction.MISSA_DELETED, id, reason, "Missa deleted",
                Map.of("type", EventType.MISSA.name(), "fromStatus", context.status().name(),
                        "gamLocationId", context.event().getLocation().getId())
        );
    }
}
