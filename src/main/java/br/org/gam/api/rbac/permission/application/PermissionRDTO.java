package br.org.gam.api.rbac.permission.application;

import java.util.UUID;

public record PermissionRDTO(
        UUID id,
        String code,
        String label,
        String description,
        boolean systemManaged
) {
    public PermissionRDTO(UUID id, String code, String label, String description) {
        this(id, code, label, description, false);
    }
}
