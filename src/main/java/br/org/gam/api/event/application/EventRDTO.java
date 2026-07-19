package br.org.gam.api.event.application;

import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.gamLocation.application.GamLocationRDTO;
import br.org.gam.api.rbac.permission.application.PermissionRDTO;
import java.time.Instant;
import java.util.UUID;

public record EventRDTO(
        UUID id,
        String title,
        String description,
        GamLocationRDTO gamLocation,
        PermissionRDTO requiredPermission,
        Instant beginDate,
        Instant endDate,
        EventType type,
        EventStatus status,
        String cancellationReason
) {
    public EventRDTO(UUID id, String title, String description, GamLocationRDTO gamLocation,
                     PermissionRDTO requiredPermission, Instant beginDate, Instant endDate,
                     EventType type, EventStatus status) {
        this(id, title, description, gamLocation, requiredPermission, beginDate, endDate, type, status, null);
    }
}
