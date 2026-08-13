package br.org.gam.api.event.missa.application.useCases;

import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.missa.application.MissaApiModels.MissaRDTO;
import br.org.gam.api.event.missa.application.MissaApiModels.ReplaceMissaDTO;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.gamLocation.persistence.GamLocationEntity;
import br.org.gam.api.rbac.permission.persistence.PermissionEntity;
import br.org.gam.api.shared.activitylog.ActivityAction;
import br.org.gam.api.shared.exception.InvalidCommandException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReplaceMissa {
    private final MissaUseCaseSupport support;

    ReplaceMissa(MissaUseCaseSupport support) {
        this.support = support;
    }

    @Transactional
    public MissaRDTO replace(UUID id, ReplaceMissaDTO dto) {
        Instant evaluationInstant = support.clock.instant();
        support.validateDates(dto.beginDate(), dto.endDate());
        String title = support.normalizeTitle(dto.title());
        String description = support.normalizeDescription(dto.description());
        String reason = support.normalizeOptionalReason(dto.reason(), "Invalid Missa update reason.");
        MissaUseCaseSupport.MutationContext context = support.mutationContext(id, evaluationInstant);
        EventEntity event = context.event();
        EventStatus fromStatus = context.status();
        if (fromStatus == EventStatus.FINALIZED || fromStatus == EventStatus.CANCELLED) {
            throw support.transitionConflict(id, fromStatus, fromStatus);
        }
        if (fromStatus == EventStatus.LOCKED && dto.endDate().isAfter(evaluationInstant)) {
            throw support.transitionConflict(id, fromStatus, EventStatus.SCHEDULED);
        }

        GamLocationEntity location = support.locationLoader.requiredByIdForUpdate(dto.gamLocationId());
        PermissionEntity audience = support.resolveAudiencePermission(dto.requiredPermissionId());
        UUID currentAudienceId = event.getRequiredPermission() == null ? null : event.getRequiredPermission().getId();
        boolean audienceChanged = !Objects.equals(currentAudienceId, dto.requiredPermissionId());
        if (audienceChanged && reason == null) {
            throw InvalidCommandException.reason("Changing a Missa audience requires an audit reason.");
        }

        List<String> changedFields = new ArrayList<>();
        if (!Objects.equals(event.getTitle(), title)) changedFields.add("title");
        if (!Objects.equals(event.getDescription(), description)) changedFields.add("description");
        if (!Objects.equals(event.getLocation().getId(), dto.gamLocationId())) changedFields.add("gamLocationId");
        if (audienceChanged) changedFields.add("requiredPermissionId");
        if (!Objects.equals(event.getBeginDate(), dto.beginDate())) changedFields.add("beginDate");
        if (!Objects.equals(event.getEndDate(), dto.endDate())) changedFields.add("endDate");
        if (changedFields.isEmpty()) return support.detail(context.missa(), evaluationInstant);

        event.setTitle(title);
        event.setDescription(description);
        event.setLocation(location);
        event.setRequiredPermission(audience);
        event.setBeginDate(dto.beginDate());
        event.setEndDate(dto.endDate());
        if (fromStatus == EventStatus.SCHEDULED || fromStatus == EventStatus.COMPLETED) {
            event.setStatus(Event.effectiveStatus(EventStatus.SCHEDULED, dto.endDate(), evaluationInstant));
        }
        support.eventRepository.saveAndFlush(event);

        EventStatus toStatus = support.effectiveStatus(event, evaluationInstant);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("changedFields", List.copyOf(changedFields));
        if (fromStatus != toStatus) {
            metadata.put("fromStatus", fromStatus.name());
            metadata.put("toStatus", toStatus.name());
        }
        support.activity(ActivityAction.MISSA_UPDATED, id, reason, "Missa updated", metadata);
        return support.detail(context.missa(), evaluationInstant);
    }
}
