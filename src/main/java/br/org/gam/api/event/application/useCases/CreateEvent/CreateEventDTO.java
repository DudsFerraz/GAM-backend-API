package br.org.gam.api.event.application.useCases.CreateEvent;

import br.org.gam.api.event.domain.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.Nullable;

public record CreateEventDTO(
        @NotNull @NotBlank String title,
        @Nullable String description,
        @NotNull UUID locationId,
        @NotNull UUID requiredPermissionId,
        @NotNull Instant beginDate,
        @NotNull Instant endDate,
        @NotNull EventType type
        ) {
}
