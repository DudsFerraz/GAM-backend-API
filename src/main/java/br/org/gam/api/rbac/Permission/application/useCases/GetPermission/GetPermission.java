package br.org.gam.api.rbac.Permission.application.useCases.GetPermission;

import br.org.gam.api.rbac.Permission.application.PermissionRDTO;
import br.org.gam.api.rbac.Permission.domain.Permission;
import java.util.UUID;

public interface GetPermission {
    PermissionRDTO byId(UUID id);
}
