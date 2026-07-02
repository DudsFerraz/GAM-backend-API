package br.org.gam.api.rbac.Permission.persistence;

import br.org.gam.api.shared.persistence.BaseRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends BaseRepository<PermissionEntity, UUID> {
}
