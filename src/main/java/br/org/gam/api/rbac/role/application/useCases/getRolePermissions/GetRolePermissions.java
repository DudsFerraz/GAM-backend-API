package br.org.gam.api.rbac.role.application.useCases.getRolePermissions;

import br.org.gam.api.rbac.permission.application.PermissionMapper;
import br.org.gam.api.rbac.permission.application.PermissionRDTO;
import br.org.gam.api.rbac.permission.persistence.PermissionEntity;
import br.org.gam.api.rbac.rolePermission.persistence.RolePermissionEntity;
import br.org.gam.api.rbac.rolePermission.persistence.RolePermissionRepository;
import br.org.gam.api.rbac.rolePermission.persistence.RolePermissionSpecifications;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class GetRolePermissions {
    private final RolePermissionRepository rolePermissionRepo;
    private final PermissionMapper permissionMapper;

    public GetRolePermissions(RolePermissionRepository rolePermissionRepo, PermissionMapper permissionMapper) {
        this.rolePermissionRepo = rolePermissionRepo;
        this.permissionMapper = permissionMapper;
    }
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
                .map(permissionMapper::entityToRDTO).toList();

        return new GetRolePermissionsRDTO(dtosList);
    }
}
