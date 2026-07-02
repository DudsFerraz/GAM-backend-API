package br.org.gam.api.rbac.Role.application;

import br.org.gam.api.rbac.Role.persistence.RoleEntity;
import br.org.gam.api.rbac.Role.persistence.RoleRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RoleEntityLoader {

    private final RoleRepository roleRepo;

    public RoleEntityLoader(RoleRepository roleRepo) {
        this.roleRepo = roleRepo;
    }

    public RoleEntity requiredById(UUID id) {
        return roleRepo.findById(id)
                .orElseThrow(() -> new RoleNotFoundException("Could not find role with id " + id));
    }

    public RoleEntity requiredByName(String name) {
        return roleRepo.findByName(name)
                .orElseThrow(() -> new RoleNotFoundException("Could not find role with name " + name));
    }
}
