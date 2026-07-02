package br.org.gam.api.rbac.Permission.application.useCases.GetPermissionInstance;

import br.org.gam.api.rbac.Permission.application.PermissionMapper;
import br.org.gam.api.rbac.Permission.application.PermissionNotFoundException;
import br.org.gam.api.rbac.Permission.domain.Permission;
import br.org.gam.api.rbac.Permission.persistence.PermissionEntity;
import br.org.gam.api.rbac.Permission.persistence.PermissionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SpringGetPermissionInstance implements GetPermissionInstance {
    private final PermissionRepository permissionRepo;
    private final PermissionMapper permissionMapper;

    public SpringGetPermissionInstance(PermissionRepository permissionRepo, PermissionMapper permissionMapper) {
        this.permissionRepo = permissionRepo;
        this.permissionMapper = permissionMapper;
    }

    @Override
    public Permission domainById(UUID id) {
        return permissionRepo.findById(id)
                .map(permissionMapper::entityToDomain)
                .orElseThrow(() -> new PermissionNotFoundException("Could not find permission with id " + id));
    }

    @Override
    public PermissionEntity entityById(UUID id) {
        return permissionRepo.findById(id)
                .orElseThrow(() -> new PermissionNotFoundException("Could not find permission with id " + id));
    }
}
