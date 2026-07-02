package br.org.gam.api.event.application;

import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.location.application.LocationRDTO;
import br.org.gam.api.rbac.Permission.application.PermissionRDTO;
import java.time.Instant;
import java.util.UUID;

public record EventRDTO(
        UUID id,
        String title,
        String description,
        LocationRDTO location,
        PermissionRDTO requiredPermission,
        Instant beginDate,
        Instant endDate,
        EventType type,
        EventStatus status
) {
}
