package br.org.gam.api.member.application.useCases;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CoordinatorTransitionDTO(
        @NotNull(message = "Coordinator transition reason is required.")
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                minLength = 1,
                maxLength = 2000,
                description = "Trimmed by removing only leading and trailing Unicode White_Space code points "
                        + "before validation; the normalized reason must not be blank and must contain from 1 "
                        + "through 2,000 Unicode code points."
        )
        String reason
) {
}
