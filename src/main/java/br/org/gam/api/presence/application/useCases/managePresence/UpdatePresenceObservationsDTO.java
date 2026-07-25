package br.org.gam.api.presence.application.useCases.managePresence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = false)
public record UpdatePresenceObservationsDTO(
        @JsonProperty(value = "observations", required = true)
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"},
                description = "Required property whose value is trimmed before validation. An explicit null, blank, "
                        + "or whitespace-only value normalizes to null. A non-null normalized value may contain at "
                        + "most 2,000 Unicode code points."
        )
        String observations
) {
}
