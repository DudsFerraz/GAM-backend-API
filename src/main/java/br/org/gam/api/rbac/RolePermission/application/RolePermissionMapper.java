package br.org.gam.api.rbac.RolePermission.application;

import br.org.gam.api.rbac.Permission.application.PermissionMapper;
import br.org.gam.api.rbac.RolePermission.domain.RolePermission;
import br.org.gam.api.rbac.RolePermission.persistence.RolePermissionEntity;
import br.org.gam.api.shared.auditing.IgnoreJunctionAuditFields;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {PermissionMapper.class})
public interface RolePermissionMapper {
    @IgnoreJunctionAuditFields
    RolePermissionEntity domainToEntity(RolePermission rolePermissionDomain);
    RolePermission entityToDomain(RolePermissionEntity rolePermissionEntity);
}
