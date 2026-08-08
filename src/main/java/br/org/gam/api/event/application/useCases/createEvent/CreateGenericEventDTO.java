package br.org.gam.api.event.application.useCases.createEvent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.Nullable;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateGenericEventDTO(
        @NotNull @NotBlank @Schema(minLength = 1, maxLength = 255) String title,
        @Nullable @Schema(maxLength = 10_000) String description,
        @NotNull UUID gamLocationId,
        @Nullable UUID requiredPermissionId,
        @NotNull Instant beginDate,
        @NotNull Instant endDate
) {
}
