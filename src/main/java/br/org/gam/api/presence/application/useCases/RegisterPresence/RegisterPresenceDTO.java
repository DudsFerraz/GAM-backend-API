package br.org.gam.api.presence.application.useCases.RegisterPresence;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.lang.Nullable;

public record RegisterPresenceDTO(
        @NotNull UUID eventId,
        @NotNull UUID memberId,
        @Nullable String observations
) {
}
