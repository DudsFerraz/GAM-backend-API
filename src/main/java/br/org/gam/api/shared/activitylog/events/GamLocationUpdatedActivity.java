package br.org.gam.api.shared.activitylog.events;

import java.util.List;
import java.util.UUID;

public record GamLocationUpdatedActivity(UUID locationId, List<String> changedFields) {
}
