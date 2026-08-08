package br.org.gam.api.rbac.role.persistence;

import br.org.gam.api.shared.persistence.BaseRepository;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends BaseRepository<RoleEntity, UUID> {
    Optional<RoleEntity> findByName(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select role from RoleEntity role where role.name = :name")
    Optional<RoleEntity> findByNameForUpdate(@Param("name") String name);
}
