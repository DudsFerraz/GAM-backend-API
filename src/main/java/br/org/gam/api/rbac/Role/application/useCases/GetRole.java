package br.org.gam.api.rbac.role.application.useCases;

import br.org.gam.api.rbac.role.application.RoleMapper;
import br.org.gam.api.rbac.role.application.RoleRDTO;
import br.org.gam.api.rbac.role.application.RoleEntityLoader;
import br.org.gam.api.rbac.role.persistence.RoleEntity;
import br.org.gam.api.rbac.role.application.RolesRDTO;
import br.org.gam.api.rbac.role.domain.SystemRole;
import br.org.gam.api.rbac.role.persistence.RoleRepository;
import br.org.gam.api.shared.exception.InvalidCommandException;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetRole {
    private final RoleEntityLoader getRoleInstance;
    private final RoleMapper roleMapper;
    private final RoleRepository roleRepository;

    public GetRole(RoleEntityLoader getRoleInstance, RoleMapper roleMapper, RoleRepository roleRepository) {
        this.getRoleInstance = getRoleInstance;
        this.roleMapper = roleMapper;
        this.roleRepository = roleRepository;
    }
    public RoleRDTO byId(UUID id) {

        RoleEntity roleEntity = getRoleInstance.requiredById(id);
        return roleMapper.entityToRDTO(roleEntity);
    }

    public RolesRDTO all(String submittedName) {
        String name = normalizeName(submittedName);
        Comparator<RoleEntity> ordering = Comparator
                .comparing(RoleEntity::getName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(RoleEntity::getId);
        Set<String> blacklist = Set.of(SystemRole.SUDO.getCode());

        return new RolesRDTO(roleRepository.findAll().stream()
                .filter(role -> !blacklist.contains(role.getName()))
                .filter(role -> !role.isSystemManaged() || SystemRole.fromCode(role.getName()).isPresent())
                .filter(role -> name == null || role.getName().toLowerCase(Locale.ROOT).contains(name))
                .sorted(ordering)
                .map(roleMapper::entityToRDTO)
                .toList());
    }

    private String normalizeName(String submittedName) {
        if (submittedName == null) return null;
        String normalized = submittedName.strip();
        if (normalized.isEmpty()) {
            throw InvalidCommandException.reason("Role name search must not be blank.");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }
}
