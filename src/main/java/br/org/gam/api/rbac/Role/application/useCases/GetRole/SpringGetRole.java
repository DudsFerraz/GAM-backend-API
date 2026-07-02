package br.org.gam.api.rbac.Role.application.useCases.GetRole;

import br.org.gam.api.rbac.Role.application.RoleMapper;
import br.org.gam.api.rbac.Role.application.RoleRDTO;
import br.org.gam.api.rbac.Role.application.useCases.GetRoleInstance.GetRoleInstance;
import br.org.gam.api.rbac.Role.domain.Role;
import br.org.gam.api.rbac.Role.persistence.RoleEntity;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SpringGetRole implements GetRole {
    private final GetRoleInstance getRoleInstance;
    private final RoleMapper roleMapper;

    public SpringGetRole(GetRoleInstance getRoleInstance, RoleMapper roleMapper) {
        this.getRoleInstance = getRoleInstance;
        this.roleMapper = roleMapper;
    }

    @Override
    public RoleRDTO byId(UUID id) {

        RoleEntity roleEntity = getRoleInstance.entityById(id);
        return roleMapper.entityToRoleRDTO(roleEntity);
    }
}
