package br.org.gam.api.rbac.Role.application;

import br.org.gam.api.rbac.Role.persistence.RoleEntity;
import br.org.gam.api.rbac.Role.persistence.RoleRepository;
import br.org.gam.api.shared.exception.NotFoundException;
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
                .orElseThrow(() -> NotFoundException.resource("Role", id));
    }

    public RoleEntity requiredByName(String name) {
        return roleRepo.findByName(name)
                .orElseThrow(() -> NotFoundException.resource("Role", name));
    }
}
