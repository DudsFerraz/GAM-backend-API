package br.org.gam.api.presence.application.useCases.registerPresence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.lang.Nullable;

@JsonIgnoreProperties(ignoreUnknown = false)
public record RegisterPresenceRequestDTO(
        @NotNull UUID memberId,
        @Nullable
        @Schema(
                types = {"string", "null"},
                description = "Trimmed before validation. An omitted, explicit null, blank, or whitespace-only "
                        + "value normalizes to null. A non-null normalized value may contain at most 2,000 "
                        + "Unicode code points."
        )
        String observations
) {
}
