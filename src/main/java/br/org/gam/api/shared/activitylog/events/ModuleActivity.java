package br.org.gam.api.shared.activitylog.events;

import br.org.gam.api.shared.activitylog.ActivityAction;
import br.org.gam.api.shared.activitylog.ActivityTargetType;
import java.util.Map;
import java.util.UUID;

public record ModuleActivity(
        ActivityAction action,
        ActivityTargetType targetType,
        UUID targetId,
        String reason,
        String summary,
        Map<String, Object> metadata
) {
}
