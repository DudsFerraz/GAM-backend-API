package br.org.gam.api.rbac.RolePermission.persistence;

import br.org.gam.api.shared.persistence.BaseRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RolePermissionRepository extends BaseRepository<RolePermissionEntity, UUID>,
                                                  JpaSpecificationExecutor<RolePermissionEntity> {
}
