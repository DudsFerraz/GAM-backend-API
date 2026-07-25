package br.org.gam.api.presence.application.useCases.managePresence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = false)
public record RemovePresenceDTO(
        @NotNull
        @NotBlank
        @Schema(
                description = "Trimmed before validation; the normalized reason must not be blank and must "
                        + "contain between 1 and 2,000 Unicode code points."
        )
        String reason
) {
}
