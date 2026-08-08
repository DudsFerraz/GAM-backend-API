package br.org.gam.api.rbac.role.web;

import br.org.gam.api.rbac.role.application.useCases.getRolePermissions.GetRolePermissions;
import br.org.gam.api.rbac.role.application.useCases.getRolePermissions.GetRolePermissionsRDTO;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.rbac.role.application.RoleEntityLoader;
import br.org.gam.api.rbac.role.application.RoleRDTO;
import br.org.gam.api.rbac.role.application.useCases.GetRole;
import br.org.gam.api.rbac.role.application.RolesRDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/roles")
@RestController
public class RoleController {
    private final GetRole getRole;
    private final GetRolePermissions getRolePermissions;
    private final RoleEntityLoader roleEntityLoader;

    public RoleController(GetRole getRole, GetRolePermissions getRolePermissions, RoleEntityLoader roleEntityLoader) {
        this.getRole = getRole;
        this.getRolePermissions = getRolePermissions;
        this.roleEntityLoader = roleEntityLoader;
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ROLE_GET + "')")
    @Operation(operationId = "listRoles", summary = "List visible Roles")
    @GetMapping
    public ResponseEntity<RolesRDTO> all(
            @Parameter(description = "Trimmed, case-insensitive and accent-sensitive Role-name substring; blank input returns HTTP 400.")
            @RequestParam(required = false) String name
    ) {
        return ResponseEntity.ok(getRole.all(name));
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ROLE_GET + "')")
    @Operation(operationId = "getRole")
    @GetMapping("/{roleId}")
    public ResponseEntity<RoleRDTO> getById(@PathVariable UUID roleId) {
        return ResponseEntity.ok(getRole.byId(roleId));
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ROLE_GET + "') and hasAuthority('"
            + PermissionEnum.Code.PERMISSION_GET + "')")
    @Operation(operationId = "getRolePermissions")
    @GetMapping("/{roleId}/permissions")
    public ResponseEntity<GetRolePermissionsRDTO> getPermissionsById(@PathVariable UUID roleId){
        roleEntityLoader.requiredById(roleId);

        return ResponseEntity.ok(
                getRolePermissions.allById(roleId)
        );
    }
}
