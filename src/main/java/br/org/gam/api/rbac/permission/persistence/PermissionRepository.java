package br.org.gam.api.rbac.permission.persistence;

import br.org.gam.api.shared.persistence.BaseRepository;
import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository extends BaseRepository<PermissionEntity, UUID> {
    Optional<PermissionEntity> findByCode(String code);
}
