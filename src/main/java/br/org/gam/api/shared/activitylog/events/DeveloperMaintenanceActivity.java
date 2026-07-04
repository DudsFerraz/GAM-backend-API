package br.org.gam.api.shared.activitylog.events;

import br.org.gam.api.shared.activitylog.ActivityAction;
import java.util.Map;
import java.util.UUID;

public record DeveloperMaintenanceActivity(
        ActivityAction action,
        UUID targetId,
        String table,
        String reason,
        String summary,
        Map<String, Object> metadata
) {
}
