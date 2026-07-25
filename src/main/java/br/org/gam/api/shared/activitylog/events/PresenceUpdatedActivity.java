package br.org.gam.api.shared.activitylog.events;

import java.util.UUID;

public record PresenceUpdatedActivity(
        UUID presenceId,
        UUID memberId,
        UUID eventId,
        String previousObservations,
        String newObservations
) {
}
