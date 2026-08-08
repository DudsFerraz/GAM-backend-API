package br.org.gam.api.event.missa.application.useCases;

import br.org.gam.api.event.application.EventSecurity;
import br.org.gam.api.event.application.EventEntityLoader;
import br.org.gam.api.event.missa.application.MissaMapper;
import br.org.gam.api.event.missa.application.MissaRDTO;
import br.org.gam.api.event.missa.application.MissaEntityLoader;
import br.org.gam.api.event.missa.persistence.MissaEntity;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.shared.exception.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetMissa {
    private final MissaMapper missaMapper;
    private final MissaEntityLoader getMissaInstance;
    private final EventEntityLoader getEventInstance;
    private final EventSecurity eventSecurity;

    public GetMissa(MissaMapper missaMapper, MissaEntityLoader getMissaInstance, EventEntityLoader getEventInstance, EventSecurity eventSecurity) {
        this.missaMapper = missaMapper;
        this.getMissaInstance = getMissaInstance;
        this.getEventInstance = getEventInstance;
        this.eventSecurity = eventSecurity;
    }
    public MissaRDTO byId(UUID id) {
        EventEntity eventEntity = getEventInstance.requiredById(id);
        if(!eventSecurity.canGetEvent(eventEntity)) throw NotFoundException.resource("Missa", id);

        MissaEntity missaEntity = getMissaInstance.requiredById(id);
        return missaMapper.entityToRDTO(missaEntity);
    }
}
