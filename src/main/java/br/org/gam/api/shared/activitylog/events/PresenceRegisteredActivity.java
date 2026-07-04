package br.org.gam.api.shared.activitylog.events;

import java.util.UUID;

public record PresenceRegisteredActivity(UUID presenceId, UUID memberId, UUID eventId) {
}
