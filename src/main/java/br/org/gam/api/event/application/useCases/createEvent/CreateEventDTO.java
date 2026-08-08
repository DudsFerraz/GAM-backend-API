package br.org.gam.api.event.application.useCases.createEvent;

import br.org.gam.api.event.domain.EventType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.Nullable;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateEventDTO(
        @NotNull @NotBlank String title,
        @Nullable String description,
        @NotNull UUID gamLocationId,
        @Nullable UUID requiredPermissionId,
        @NotNull Instant beginDate,
        @NotNull Instant endDate,
        @Nullable EventType type
) {
    public CreateEventDTO(String title, String description, UUID gamLocationId, UUID requiredPermissionId,
                          Instant beginDate, Instant endDate) {
        this(title, description, gamLocationId, requiredPermissionId, beginDate, endDate, null);
    }
}
