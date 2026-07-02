package br.org.gam.api.rbac.Role.application.useCases.GetRoleInstance;

import br.org.gam.api.rbac.Role.application.RoleNotFoundException;
import br.org.gam.api.rbac.Role.persistence.RoleEntity;
import br.org.gam.api.rbac.Role.persistence.RoleRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetRoleInstance {
    private final RoleRepository roleRepo;

    public GetRoleInstance(RoleRepository roleRepo) {
        this.roleRepo = roleRepo;
    }
    public RoleEntity entityById(UUID id) {
        return roleRepo.findById(id)
                .orElseThrow(() -> new RoleNotFoundException("Could not find role with id " + id));
    }
    public RoleEntity entityByName(String name) {
        return roleRepo.findByName(name)
                .orElseThrow(() -> new RoleNotFoundException("Could not find role with name " + name));
    }
}
