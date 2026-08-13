package br.org.gam.api.event.missa.application.useCases;

import br.org.gam.api.event.missa.application.MissaApiModels.MissaRDTO;
import br.org.gam.api.event.missa.persistence.MissaEntity;
import br.org.gam.api.shared.exception.NotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetMissa {
    private final MissaUseCaseSupport support;

    GetMissa(MissaUseCaseSupport support) {
        this.support = support;
    }

    @Transactional(readOnly = true)
    public MissaRDTO byId(UUID id) {
        Instant evaluationInstant = support.clock.instant();
        MissaEntity missa = support.required(id);
        if (!support.eventSecurity.canGetEvent(missa.getEvent())) {
            throw NotFoundException.resource("Missa", id);
        }
        return support.detail(missa, evaluationInstant);
    }
}
