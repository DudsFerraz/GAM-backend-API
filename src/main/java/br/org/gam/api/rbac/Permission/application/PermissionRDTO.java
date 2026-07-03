package br.org.gam.api.rbac.Permission.application;

import java.util.UUID;

public record PermissionRDTO(
        UUID id,
        String name,
        String description,
        boolean systemManaged
) {
    public PermissionRDTO(UUID id, String name, String description) {
        this(id, name, description, false);
    }
}
