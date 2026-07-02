package br.org.gam.api.rbac.Role.application;

import br.org.gam.api.rbac.Role.persistence.RoleEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RoleMapper {

    // =====================================================================================
    // Persistence -> RDTO
    // =====================================================================================

    RoleRDTO entityToRDTO(RoleEntity roleEntity);
}
