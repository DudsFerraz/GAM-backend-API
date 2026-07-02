package br.org.gam.api.rbac.Role.application.useCases.GetRolePermissions;

import br.org.gam.api.rbac.Permission.application.PermissionMapper;
import br.org.gam.api.rbac.Permission.application.PermissionRDTO;
import br.org.gam.api.rbac.Permission.domain.Permission;
import br.org.gam.api.rbac.Permission.persistence.PermissionEntity;
import br.org.gam.api.rbac.Role.domain.Role;
import br.org.gam.api.rbac.RolePermission.domain.RolePermission;
import br.org.gam.api.rbac.RolePermission.persistence.RolePermissionEntity;
import br.org.gam.api.rbac.RolePermission.persistence.RolePermissionRepository;
import br.org.gam.api.rbac.RolePermission.persistence.RolePermissionSpecifications;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class SpringGetRolePermissions implements GetRolePermissions {
    private final RolePermissionRepository rolePermissionRepo;
    private final PermissionMapper permissionMapper;

    public SpringGetRolePermissions(RolePermissionRepository rolePermissionRepo, PermissionMapper permissionMapper) {
        this.rolePermissionRepo = rolePermissionRepo;
        this.permissionMapper = permissionMapper;
    }

    @Override
    public GetRolePermissionsRDTO allById(UUID roleId) {
        Specification<RolePermissionEntity> spec = RolePermissionSpecifications.filterByRoleId(roleId)
                .and(RolePermissionSpecifications.fetchPermission())
                .and(RolePermissionSpecifications.fetchRole());

        List<RolePermissionEntity> rolePermissionEntities = rolePermissionRepo.findAll(spec);

        List<PermissionEntity> permissionEntities = rolePermissionEntities
                .stream()
                .map(RolePermissionEntity::getPermission).toList();

        List<PermissionRDTO> dtosList = permissionEntities
                .stream()
                .map(permissionMapper::entityToPermissionRDTO).toList();

        return new GetRolePermissionsRDTO(dtosList);
    }
}
