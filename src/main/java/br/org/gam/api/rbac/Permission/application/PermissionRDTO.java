package br.org.gam.api.rbac.Permission.application;

import java.util.UUID;

public record PermissionRDTO(
        UUID id,
        String name,
        String description
) {
}
