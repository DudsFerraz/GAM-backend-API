package br.org.gam.api.rbac.permission.application.useCases;

import br.org.gam.api.rbac.permission.application.PermissionMapper;
import br.org.gam.api.rbac.permission.application.PermissionRDTO;
import br.org.gam.api.rbac.permission.application.PermissionEntityLoader;
import br.org.gam.api.rbac.permission.persistence.PermissionEntity;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetPermission {
    private final PermissionEntityLoader getPermissionInstance;
    private final PermissionMapper permissionMapper;

    public GetPermission(PermissionEntityLoader getPermissionInstance, PermissionMapper permissionMapper) {
        this.getPermissionInstance = getPermissionInstance;
        this.permissionMapper = permissionMapper;
    }
    public PermissionRDTO byId(UUID id) {

        PermissionEntity permissionEntity = getPermissionInstance.requiredById(id);
        return permissionMapper.entityToRDTO(permissionEntity);
    }
}
