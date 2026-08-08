package br.org.gam.api.rbac.permission.application;

import br.org.gam.api.rbac.permission.persistence.PermissionEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PermissionMapper {

    // =====================================================================================
    // Persistence -> RDTO
    // =====================================================================================

    PermissionRDTO entityToRDTO(PermissionEntity permissionEntity);
}
