package br.org.gam.api.shared.activitylog.events;

import java.util.UUID;

public record MissaCreatedActivity(UUID missaId, UUID eventId) {
}
