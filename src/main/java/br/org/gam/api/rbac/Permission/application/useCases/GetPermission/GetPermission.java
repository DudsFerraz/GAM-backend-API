package br.org.gam.api.rbac.Permission.application.useCases.GetPermission;

import br.org.gam.api.rbac.Permission.application.PermissionMapper;
import br.org.gam.api.rbac.Permission.application.PermissionRDTO;
import br.org.gam.api.rbac.Permission.application.useCases.GetPermissionInstance.GetPermissionInstance;
import br.org.gam.api.rbac.Permission.persistence.PermissionEntity;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetPermission {
    private final GetPermissionInstance getPermissionInstance;
    private final PermissionMapper permissionMapper;

    public GetPermission(GetPermissionInstance getPermissionInstance, PermissionMapper permissionMapper) {
        this.getPermissionInstance = getPermissionInstance;
        this.permissionMapper = permissionMapper;
    }
    public PermissionRDTO byId(UUID id) {

        PermissionEntity permissionEntity = getPermissionInstance.entityById(id);
        return permissionMapper.entityToPermissionRDTO(permissionEntity);
    }
}
