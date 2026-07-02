package br.org.gam.api.rbac.Permission.application.useCases.GetPermissionInstance;

import br.org.gam.api.rbac.Permission.application.PermissionNotFoundException;
import br.org.gam.api.rbac.Permission.persistence.PermissionEntity;
import br.org.gam.api.rbac.Permission.persistence.PermissionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetPermissionInstance {
    private final PermissionRepository permissionRepo;

    public GetPermissionInstance(PermissionRepository permissionRepo) {
        this.permissionRepo = permissionRepo;
    }
    public PermissionEntity entityById(UUID id) {
        return permissionRepo.findById(id)
                .orElseThrow(() -> new PermissionNotFoundException("Could not find permission with id " + id));
    }
}
