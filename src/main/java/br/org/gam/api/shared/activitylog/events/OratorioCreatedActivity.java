package br.org.gam.api.shared.activitylog.events;

import java.util.UUID;

public record OratorioCreatedActivity(UUID oratorioId, UUID eventId) {
}
