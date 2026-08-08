package br.org.gam.api.rbac.role.application;

import br.org.gam.api.rbac.role.persistence.RoleEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RoleMapper {

    // =====================================================================================
    // Persistence -> RDTO
    // =====================================================================================

    RoleRDTO entityToRDTO(RoleEntity roleEntity);
}
