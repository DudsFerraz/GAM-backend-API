package br.org.gam.api.rbac.Permission.application.useCases;

import br.org.gam.api.rbac.Permission.application.PermissionMapper;
import br.org.gam.api.rbac.Permission.application.PermissionRDTO;
import br.org.gam.api.rbac.Permission.application.PermissionEntityLoader;
import br.org.gam.api.rbac.Permission.persistence.PermissionEntity;
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
        return permissionMapper.entityToPermissionRDTO(permissionEntity);
    }
}
