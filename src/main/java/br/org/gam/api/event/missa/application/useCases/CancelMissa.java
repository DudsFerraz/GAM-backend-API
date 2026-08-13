package br.org.gam.api.event.missa.application.useCases;

import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.missa.application.MissaApiModels.MissaRDTO;
import br.org.gam.api.shared.activitylog.ActivityAction;
import br.org.gam.api.shared.validation.RequiredReason;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelMissa {
    private final MissaUseCaseSupport support;

    CancelMissa(MissaUseCaseSupport support) {
        this.support = support;
    }

    @Transactional
    public MissaRDTO cancel(UUID id, String rawReason) {
        String reason = RequiredReason.normalize(rawReason, "Missa cancellation requires an audit reason.");
        Instant evaluationInstant = support.clock.instant();
        MissaUseCaseSupport.MutationContext context = support.mutationContext(id, evaluationInstant);
        if (context.status() != EventStatus.SCHEDULED) {
            throw support.transitionConflict(id, context.status(), EventStatus.CANCELLED);
        }
        context.event().setStatus(EventStatus.CANCELLED);
        context.event().setCancellationReason(reason);
        support.eventRepository.saveAndFlush(context.event());
        support.activity(ActivityAction.MISSA_CANCELLED, id, reason, "Missa lifecycle changed",
                Map.of("fromStatus", context.status().name(), "toStatus", EventStatus.CANCELLED.name()));
        return support.detail(context.missa(), evaluationInstant);
    }
}
