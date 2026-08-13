package br.org.gam.api.event.missa.application.useCases;

import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.missa.application.MissaApiModels.MissaRDTO;
import br.org.gam.api.shared.activitylog.ActivityAction;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinalizeMissa {
    private final MissaUseCaseSupport support;

    FinalizeMissa(MissaUseCaseSupport support) {
        this.support = support;
    }

    @Transactional
    public MissaRDTO finalizeMissa(UUID id) {
        Instant evaluationInstant = support.clock.instant();
        MissaUseCaseSupport.MutationContext context = support.mutationContext(id, evaluationInstant);
        if (context.status() != EventStatus.COMPLETED && context.status() != EventStatus.LOCKED) {
            throw support.transitionConflict(id, context.status(), EventStatus.FINALIZED);
        }
        context.event().setStatus(EventStatus.FINALIZED);
        context.event().setCancellationReason(null);
        support.eventRepository.saveAndFlush(context.event());
        support.activity(ActivityAction.MISSA_FINALIZED, id, null, "Missa lifecycle changed",
                Map.of("fromStatus", context.status().name(), "toStatus", EventStatus.FINALIZED.name()));
        return support.detail(context.missa(), evaluationInstant);
    }
}
