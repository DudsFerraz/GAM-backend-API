package br.org.gam.api.rbac.Role.application.useCases.GetRolePermissions;

import br.org.gam.api.rbac.Role.domain.Role;
import java.util.UUID;

public interface GetRolePermissions {
    GetRolePermissionsRDTO allById(UUID roleId);
}
