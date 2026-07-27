package br.org.gam.api.presence.application.useCases.managePresence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = false)
public record RemovePresenceDTO(
        @NotNull
        @Schema(
                minLength = 1,
                description = "Trimmed by removing only leading and trailing Unicode White_Space code points "
                        + "before validation; the normalized reason must not be blank and must contain from 1 "
                        + "through 2,000 Unicode code points."
        )
        String reason
) {
}
