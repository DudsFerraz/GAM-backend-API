package br.org.gam.api.rbac.Role.application.useCases.GetRoleInstance;

import br.org.gam.api.rbac.Role.domain.Role;
import br.org.gam.api.rbac.Role.persistence.RoleEntity;
import java.util.UUID;

public interface GetRoleInstance {
    Role domainById(UUID id);
    RoleEntity entityById(UUID id);
    RoleEntity entityByName(String name);
}
