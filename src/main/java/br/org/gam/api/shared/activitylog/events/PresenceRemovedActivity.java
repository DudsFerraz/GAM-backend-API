package br.org.gam.api.shared.activitylog.events;

import java.util.UUID;

public record PresenceRemovedActivity(
        UUID presenceId,
        UUID memberId,
        UUID eventId,
        String observations,
        String reason
) {
}
