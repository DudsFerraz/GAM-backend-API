package br.org.gam.api.rbac.Permission.application.useCases.GetPermissionInstance;

import br.org.gam.api.rbac.Permission.persistence.PermissionEntity;
import java.util.UUID;

public interface GetPermissionInstance {
    PermissionEntity entityById(UUID id);
}
