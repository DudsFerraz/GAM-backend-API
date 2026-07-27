package br.org.gam.api.rbac.accountRole.application.useCases;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record DropAccountRoleDTO(
        @NotNull
        @Schema(
                minLength = 1,
                description = "Leading and trailing Unicode White_Space code points are removed before validation; "
                        + "the normalized reason must contain from 1 through 2,000 Unicode code points."
        )
        String reason
) {
}
