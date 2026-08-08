package br.org.gam.api.rbac.permission.application;

import br.org.gam.api.rbac.permission.persistence.PermissionEntity;
import br.org.gam.api.rbac.permission.persistence.PermissionRepository;
import br.org.gam.api.shared.exception.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PermissionEntityLoader {

    private final PermissionRepository permissionRepo;

    public PermissionEntityLoader(PermissionRepository permissionRepo) {
        this.permissionRepo = permissionRepo;
    }

    public PermissionEntity requiredById(UUID id) {
        return permissionRepo.findById(id)
                .orElseThrow(() -> NotFoundException.resource("Permission", id));
    }
}
