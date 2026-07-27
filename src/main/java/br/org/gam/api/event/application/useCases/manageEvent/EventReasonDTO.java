package br.org.gam.api.event.application.useCases.manageEvent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = false)
public record EventReasonDTO(
        @NotNull
        @Schema(
                minLength = 1,
                description = "Leading and trailing Unicode White_Space code points are removed before validation; "
                        + "the normalized reason must contain from 1 through 2,000 Unicode code points."
        )
        String reason
) {
}
