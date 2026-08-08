package br.org.gam.api.presence.application.useCases.registerPresence;

import br.org.gam.api.presence.application.PresenceEventRDTO;
import br.org.gam.api.presence.application.PresenceMemberRDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record RegisterPresenceRDTO(
        @NotNull UUID id,
        @NotNull PresenceMemberRDTO member,
        @NotNull PresenceEventRDTO event,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"},
                maxLength = 2_000
        )
        String observations,
        @NotNull Instant registeredAt
) {
}
