package br.org.gam.api.rbac.Role.application.useCases.GetRoleInstance;

import br.org.gam.api.rbac.Role.application.RoleNotFoundException;
import br.org.gam.api.rbac.Role.persistence.RoleEntity;
import br.org.gam.api.rbac.Role.persistence.RoleRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SpringGetRoleInstance implements GetRoleInstance {
    private final RoleRepository roleRepo;

    public SpringGetRoleInstance(RoleRepository roleRepo) {
        this.roleRepo = roleRepo;
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
