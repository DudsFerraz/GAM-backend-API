package br.org.gam.api.rbac.Role.application;

import br.org.gam.api.rbac.Role.domain.Role;
import java.util.UUID;

public record RoleRDTO(
        UUID id,
        String name,
        String description
) {
}
