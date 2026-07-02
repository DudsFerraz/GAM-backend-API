package br.org.gam.api.rbac.Role.persistence;

import br.org.gam.api.shared.persistence.BaseRepository;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends BaseRepository<RoleEntity, UUID> {
    Optional<RoleEntity> findByName(String name);
}
