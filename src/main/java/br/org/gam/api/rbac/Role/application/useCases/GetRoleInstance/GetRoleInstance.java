package br.org.gam.api.rbac.Role.application.useCases.GetRoleInstance;

import br.org.gam.api.rbac.Role.persistence.RoleEntity;
import java.util.UUID;

public interface GetRoleInstance {
    RoleEntity entityById(UUID id);
    RoleEntity entityByName(String name);
}
