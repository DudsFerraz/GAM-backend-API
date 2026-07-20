package br.org.gam.api.member.application.useCases;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CoordinatorTransitionDTO(
        @NotBlank(message = "Coordinator transition reason is required.")
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                minLength = 1,
                description = "Trimmed before validation; the normalized reason must contain at most 2,000 Unicode code points."
        )
        String reason
) {
}
