package br.org.gam.api.shared.activitylog.events;

import java.util.UUID;

public record GamLocationRemovedActivity(UUID locationId, String reason, String name) {
}
