package br.org.gam.api.rbac.Role.application.useCases.GetRolePermissions;

import br.org.gam.api.rbac.Permission.application.PermissionRDTO;
import java.util.List;

public record GetRolePermissionsRDTO(
        List<PermissionRDTO> permissions
) {
}
