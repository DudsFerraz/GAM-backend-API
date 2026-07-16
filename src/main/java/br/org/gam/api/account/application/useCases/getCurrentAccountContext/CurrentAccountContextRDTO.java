package br.org.gam.api.account.application.useCases.getCurrentAccountContext;

import br.org.gam.api.rbac.role.application.RoleRDTO;
import br.org.gam.api.shared.domain.GamEmail;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Schema(description = "Current authenticated Account identity and effective authorization context.")
public record CurrentAccountContextRDTO(
        @Schema(format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(type = "string", format = "email", requiredMode = Schema.RequiredMode.REQUIRED)
        GamEmail email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String displayName,
        @ArraySchema(
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = RoleRDTO.class)
        )
        List<RoleRDTO> roles,
        @ArraySchema(
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(type = "string"),
                uniqueItems = true
        )
        Set<String> permissions
) {
    public CurrentAccountContextRDTO {
        roles = roles == null ? List.of() : List.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
