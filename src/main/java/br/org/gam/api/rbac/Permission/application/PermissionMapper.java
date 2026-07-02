package br.org.gam.api.rbac.Permission.application;

import br.org.gam.api.rbac.Permission.domain.Permission;
import br.org.gam.api.rbac.Permission.persistence.PermissionEntity;
import br.org.gam.api.shared.auditing.IgnoreFullAuditFields;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PermissionMapper {
    @IgnoreFullAuditFields
    PermissionEntity domainToEntity(Permission permissionDomain);
    Permission entityToDomain(PermissionEntity permissionEntity);
    PermissionRDTO entityToPermissionRDTO(PermissionEntity permissionEntity);
}
