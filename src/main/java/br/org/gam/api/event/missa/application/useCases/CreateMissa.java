package br.org.gam.api.event.missa.application.useCases;

import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.event.missa.application.MissaApiModels.CreateMissaDTO;
import br.org.gam.api.event.missa.application.MissaApiModels.MissaRDTO;
import br.org.gam.api.event.missa.persistence.MissaEntity;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.gamLocation.persistence.GamLocationEntity;
import br.org.gam.api.rbac.permission.persistence.PermissionEntity;
import br.org.gam.api.shared.activitylog.ActivityAction;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateMissa {
    private final MissaUseCaseSupport support;

    CreateMissa(MissaUseCaseSupport support) {
        this.support = support;
    }

    @Transactional
    public MissaRDTO create(CreateMissaDTO dto) {
        Instant evaluationInstant = support.clock.instant();
        support.validateDates(dto.beginDate(), dto.endDate());
        String title = support.normalizeTitle(dto.title());
        String description = support.normalizeDescription(dto.description());
        GamLocationEntity location = support.locationLoader.requiredByIdForUpdate(dto.gamLocationId());
        PermissionEntity audience = support.resolveAudiencePermission(dto.requiredPermissionId());

        Event event = Event.register(
                title, description, dto.beginDate(), dto.endDate(), EventType.MISSA, evaluationInstant
        );
        EventEntity eventEntity = support.eventMapper.domainToEntity(event);
        eventEntity.setLocation(location);
        eventEntity.setRequiredPermission(audience);
        support.eventRepository.save(eventEntity);

        MissaEntity missa = new MissaEntity();
        missa.setId(event.getId());
        missa.setEvent(eventEntity);
        support.missaRepository.saveAndFlush(missa);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", EventType.MISSA.name());
        metadata.put("status", event.getStatus().name());
        metadata.put("gamLocationId", dto.gamLocationId());
        metadata.put("requiredPermissionId", dto.requiredPermissionId());
        support.activity(ActivityAction.MISSA_CREATED, event.getId(), null, "Missa created", metadata);
        return support.detail(missa, evaluationInstant);
    }
}
