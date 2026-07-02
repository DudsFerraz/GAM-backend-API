package br.org.gam.api.rbac.RolePermission.application;

import br.org.gam.api.rbac.Permission.application.PermissionMapper;
import br.org.gam.api.rbac.RolePermission.persistence.RolePermissionEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {PermissionMapper.class})
public interface RolePermissionMapper {
}
