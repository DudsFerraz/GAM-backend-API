package br.org.gam.api.event.missa.application.useCases;

import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.missa.application.MissaApiModels.MissaRDTO;
import br.org.gam.api.event.missa.application.MissaApiModels.ReopenDTO;
import br.org.gam.api.event.missa.application.MissaApiModels.ReopenTargetStatus;
import br.org.gam.api.shared.activitylog.ActivityAction;
import br.org.gam.api.shared.exception.InvalidCommandException;
import br.org.gam.api.shared.validation.RequiredReason;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReopenMissa {
    private final MissaUseCaseSupport support;

    ReopenMissa(MissaUseCaseSupport support) {
        this.support = support;
    }

    @Transactional
    public MissaRDTO reopen(UUID id, ReopenDTO dto) {
        String reason = RequiredReason.normalize(dto.reason(), "Missa reopening requires an audit reason.");
        EventStatus target;
        if (dto.targetStatus() == ReopenTargetStatus.COMPLETED) {
            target = EventStatus.COMPLETED;
        } else if (dto.targetStatus() == ReopenTargetStatus.LOCKED) {
            target = EventStatus.LOCKED;
        } else {
            throw InvalidCommandException.reason("Reopening targetStatus must be LOCKED or COMPLETED.");
        }
        Instant evaluationInstant = support.clock.instant();
        MissaUseCaseSupport.MutationContext context = support.mutationContext(id, evaluationInstant);
        boolean allowed = target == EventStatus.COMPLETED
                ? context.status() == EventStatus.LOCKED || context.status() == EventStatus.FINALIZED
                : context.status() == EventStatus.FINALIZED;
        if (!allowed) throw support.transitionConflict(id, context.status(), target);
        context.event().setStatus(target);
        context.event().setCancellationReason(null);
        support.eventRepository.saveAndFlush(context.event());
        support.activity(ActivityAction.MISSA_REOPENED, id, reason, "Missa lifecycle changed",
                Map.of("fromStatus", context.status().name(), "toStatus", target.name()));
        return support.detail(context.missa(), evaluationInstant);
    }
}
