package br.org.gam.api.rbac.Role.application;

import br.org.gam.api.rbac.Role.domain.Role;
import br.org.gam.api.rbac.Role.persistence.RoleEntity;
import br.org.gam.api.shared.auditing.IgnoreFullAuditFields;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RoleMapper {
    @IgnoreFullAuditFields
    RoleEntity domainToEntity(Role role);
    Role entityToDomain(RoleEntity roleEntity);
    RoleRDTO entityToRoleRDTO(RoleEntity roleEntity);
}
