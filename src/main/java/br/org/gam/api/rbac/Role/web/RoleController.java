package br.org.gam.api.rbac.Role.web;

import br.org.gam.api.rbac.Role.application.useCases.GetRolePermissions.GetRolePermissions;
import br.org.gam.api.rbac.Role.application.useCases.GetRolePermissions.GetRolePermissionsRDTO;
import br.org.gam.api.rbac.Role.domain.Role;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/role")
@RestController
public class RoleController {
    private final GetRolePermissions getRolePermissions;

    public RoleController(GetRolePermissions getRolePermissions) {
        this.getRolePermissions = getRolePermissions;
    }

    @GetMapping("/{roleId}/permissions")
    public ResponseEntity<GetRolePermissionsRDTO> getPermissionsById(@PathVariable UUID roleId){

        return ResponseEntity.ok(
                getRolePermissions.allById(roleId)
        );
    }
}
