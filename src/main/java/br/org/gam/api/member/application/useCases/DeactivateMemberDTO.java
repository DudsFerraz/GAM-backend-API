package br.org.gam.api.member.application.useCases;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record DeactivateMemberDTO(
        @NotNull(message = "Deactivation reason is required.")
        @Schema(
                minLength = 1,
                description = "Leading and trailing Unicode White_Space code points are removed before validation; "
                        + "the normalized reason must contain from 1 through 2,000 Unicode code points."
        )
        String reason
) {
}
