package br.org.gam.api.rbac.Permission.application;

import br.org.gam.api.rbac.Permission.domain.Permission;
import java.util.UUID;

public record PermissionRDTO(
        UUID id,
        String name,
        String description
) {
}
