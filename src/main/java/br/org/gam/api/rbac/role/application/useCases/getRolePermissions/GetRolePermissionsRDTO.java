package br.org.gam.api.rbac.role.application.useCases.getRolePermissions;

import br.org.gam.api.rbac.permission.application.PermissionRDTO;
import java.util.List;

public record GetRolePermissionsRDTO(
        List<PermissionRDTO> permissions
) {
}
