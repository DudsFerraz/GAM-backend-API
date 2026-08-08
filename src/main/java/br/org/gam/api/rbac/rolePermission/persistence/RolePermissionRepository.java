package br.org.gam.api.rbac.rolePermission.persistence;

import br.org.gam.api.shared.persistence.BaseRepository;
import br.org.gam.api.shared.persistence.ReadOnlySpecificationExecutor;
import java.util.UUID;

public interface RolePermissionRepository extends BaseRepository<RolePermissionEntity, UUID>,
                                                  ReadOnlySpecificationExecutor<RolePermissionEntity> {
}
