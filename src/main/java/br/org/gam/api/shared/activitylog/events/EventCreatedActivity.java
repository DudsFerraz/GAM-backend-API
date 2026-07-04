package br.org.gam.api.shared.activitylog.events;

import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.domain.EventType;
import java.util.UUID;

public record EventCreatedActivity(
        UUID eventId,
        String title,
        EventType eventType,
        EventStatus status,
        UUID locationId,
        UUID requiredPermissionId
) {
}
