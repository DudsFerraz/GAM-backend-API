package br.org.gam.api.rbac.permission.web;

import br.org.gam.api.rbac.permission.application.PermissionRDTO;
import br.org.gam.api.rbac.permission.application.useCases.GetPermission;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/permissions")
public class PermissionController {

    private final GetPermission getPermission;

    public PermissionController(GetPermission getPermission) {
        this.getPermission = getPermission;
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.PERMISSION_GET + "')")
    @Operation(operationId = "getPermission")
    @GetMapping("/{permissionId}")
    public ResponseEntity<PermissionRDTO> getById(@PathVariable UUID permissionId) {
        return ResponseEntity.ok(getPermission.byId(permissionId));
    }
}
