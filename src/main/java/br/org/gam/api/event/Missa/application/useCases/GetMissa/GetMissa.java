package br.org.gam.api.event.Missa.application.useCases.GetMissa;

import br.org.gam.api.event.application.EventNotFoundException;
import br.org.gam.api.event.application.EventSecurity;
import br.org.gam.api.event.application.useCases.GetEventInstance.GetEventInstance;
import br.org.gam.api.event.Missa.application.MissaMapper;
import br.org.gam.api.event.Missa.application.MissaRDTO;
import br.org.gam.api.event.Missa.application.useCases.GetMissaInstance.GetMissaInstance;
import br.org.gam.api.event.Missa.domain.Missa;
import br.org.gam.api.event.Missa.persistence.MissaEntity;
import br.org.gam.api.event.persistence.EventEntity;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetMissa {
    private final MissaMapper missaMapper;
    private final GetMissaInstance getMissaInstance;
    private final GetEventInstance getEventInstance;
    private final EventSecurity eventSecurity;

    public GetMissa(MissaMapper missaMapper, GetMissaInstance getMissaInstance, GetEventInstance getEventInstance, EventSecurity eventSecurity) {
        this.missaMapper = missaMapper;
        this.getMissaInstance = getMissaInstance;
        this.getEventInstance = getEventInstance;
        this.eventSecurity = eventSecurity;
    }
    public MissaRDTO byId(UUID id) {
        EventEntity eventEntity = getEventInstance.entityById(id);
        if(!eventSecurity.canGetEvent(eventEntity)) throw new EventNotFoundException("Could not find missa with id " + id);

        MissaEntity missaEntity = getMissaInstance.entityById(id);
        return missaMapper.entityToRDTO(missaEntity);
    }
}
