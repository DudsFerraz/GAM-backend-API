package br.org.gam.api.rbac.role.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record RolesRDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<RoleRDTO> roles
) {
}
