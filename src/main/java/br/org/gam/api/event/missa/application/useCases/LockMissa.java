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
public class LockMissa {
    private final MissaUseCaseSupport support;

    LockMissa(MissaUseCaseSupport support) {
        this.support = support;
    }

    @Transactional
    public MissaRDTO lock(UUID id) {
        Instant evaluationInstant = support.clock.instant();
        MissaUseCaseSupport.MutationContext context = support.mutationContext(id, evaluationInstant);
        if (context.status() != EventStatus.COMPLETED) {
            throw support.transitionConflict(id, context.status(), EventStatus.LOCKED);
        }
        context.event().setStatus(EventStatus.LOCKED);
        context.event().setCancellationReason(null);
        support.eventRepository.saveAndFlush(context.event());
        support.activity(ActivityAction.MISSA_LOCKED, id, null, "Missa lifecycle changed",
                Map.of("fromStatus", context.status().name(), "toStatus", EventStatus.LOCKED.name()));
        return support.detail(context.missa(), evaluationInstant);
    }
}
