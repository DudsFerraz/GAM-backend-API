package br.org.gam.api.rbac.Permission.application;

import br.org.gam.api.rbac.Permission.persistence.PermissionEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PermissionMapper {

    // =====================================================================================
    // Persistence -> RDTO
    // =====================================================================================

    PermissionRDTO entityToRDTO(PermissionEntity permissionEntity);
}
