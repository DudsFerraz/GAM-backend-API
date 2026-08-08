package br.org.gam.api.rbac.role.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record RoleRDTO(
        @Schema(format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean systemManaged
) {
    public RoleRDTO(UUID id, String name, String description) {
        this(id, name, description, false);
    }
}
