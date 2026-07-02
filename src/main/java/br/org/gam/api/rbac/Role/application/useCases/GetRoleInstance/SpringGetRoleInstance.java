package br.org.gam.api.rbac.Role.application.useCases.GetRoleInstance;

import br.org.gam.api.rbac.Role.application.RoleMapper;
import br.org.gam.api.rbac.Role.application.RoleNotFoundException;
import br.org.gam.api.rbac.Role.domain.Role;
import br.org.gam.api.rbac.Role.persistence.RoleEntity;
import br.org.gam.api.rbac.Role.persistence.RoleRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SpringGetRoleInstance implements GetRoleInstance {
    private final RoleRepository roleRepo;
    private final RoleMapper roleMapper;

    public SpringGetRoleInstance(RoleRepository roleRepo, RoleMapper roleMapper) {
        this.roleRepo = roleRepo;
        this.roleMapper = roleMapper;
    }

    @Override
    public Role domainById(UUID id) {
        return roleRepo.findById(id)
                .map(roleMapper::entityToDomain)
                .orElseThrow(() -> new RoleNotFoundException("Could not find role with id " + id));
    }

    @Override
    public RoleEntity entityById(UUID id) {
        return roleRepo.findById(id)
                .orElseThrow(() -> new RoleNotFoundException("Could not find role with id " + id));
    }

    @Override
    public RoleEntity entityByName(String name) {
        return roleRepo.findByName(name)
                .orElseThrow(() -> new RoleNotFoundException("Could not find role with name " + name));
    }
}
