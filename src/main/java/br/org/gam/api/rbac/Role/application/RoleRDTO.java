package br.org.gam.api.rbac.Role.application;

import java.util.UUID;

public record RoleRDTO(
        UUID id,
        String name,
        String description,
        boolean systemManaged
) {
    public RoleRDTO(UUID id, String name, String description) {
        this(id, name, description, false);
    }
}
