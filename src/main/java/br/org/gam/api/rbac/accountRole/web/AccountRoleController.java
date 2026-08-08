package br.org.gam.api.rbac.accountRole.web;

import br.org.gam.api.rbac.accountRole.application.AccountRoleDTO;
import br.org.gam.api.rbac.accountRole.application.AccountRoleRDTO;
import br.org.gam.api.rbac.accountRole.application.AccountRolesRDTO;
import br.org.gam.api.rbac.accountRole.application.useCases.AddAccountRole;
import br.org.gam.api.rbac.accountRole.application.useCases.AddAccountRoleDTO;
import br.org.gam.api.rbac.accountRole.application.useCases.DropAccountRole;
import br.org.gam.api.rbac.accountRole.application.useCases.DropAccountRoleDTO;
import br.org.gam.api.rbac.accountRole.application.useCases.GetAccountRoles;
import br.org.gam.api.rbac.accountRole.application.useCases.GetAccountRoleAssignment;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.shared.web.PublicApiUri;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts/{accountId}")
public class AccountRoleController {
    private final GetAccountRoles getAccountRoles;
    private final AddAccountRole addAccountRole;
    private final DropAccountRole dropAccountRole;
    private final GetAccountRoleAssignment getAccountRoleAssignment;

    public AccountRoleController(GetAccountRoles getAccountRoles, AddAccountRole addAccountRole,
                                 DropAccountRole dropAccountRole,
                                 GetAccountRoleAssignment getAccountRoleAssignment) {
        this.getAccountRoles = getAccountRoles;
        this.addAccountRole = addAccountRole;
        this.dropAccountRole = dropAccountRole;
        this.getAccountRoleAssignment = getAccountRoleAssignment;
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ACCOUNT_GET + "')")
    @Operation(operationId = "getAccountRoles")
    @GetMapping("/roles")
    public ResponseEntity<AccountRolesRDTO> getRoles(@PathVariable UUID accountId) {
        return ResponseEntity.ok(getAccountRoles.get(accountId));
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ACCOUNT_ROLE_MANAGE + "')")
    @Operation(operationId = "assignAccountRole")
    @PostMapping("/roles")
    public ResponseEntity<AccountRoleRDTO> addRole(@PathVariable UUID accountId,
                                                   @RequestBody @Valid AddAccountRoleDTO request) {
        AccountRoleRDTO response = addAccountRole.byDTO(
                new AccountRoleDTO(accountId, request.roleId(), request.reason())
        );
        return ResponseEntity.created(PublicApiUri.forResource(
                "/accounts/" + accountId + "/role-assignments/" + response.assignmentId()
        )).body(response);
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ACCOUNT_ROLE_MANAGE + "')")
    @Operation(operationId = "dropAccountRole")
    @PatchMapping("/roles/{roleId}/drop")
    public ResponseEntity<Void> dropRole(@PathVariable UUID accountId, @PathVariable UUID roleId,
                                         @RequestBody @Valid DropAccountRoleDTO request) {
        dropAccountRole.byDTO(new AccountRoleDTO(accountId, roleId, request.reason()));
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ACCOUNT_GET + "')")
    @Operation(operationId = "getAccountRoleAssignment")
    @GetMapping("/role-assignments/{assignmentId}")
    public ResponseEntity<AccountRoleRDTO> getAssignment(@PathVariable UUID accountId,
                                                          @PathVariable UUID assignmentId) {
        return ResponseEntity.ok(getAccountRoleAssignment.get(accountId, assignmentId));
    }
}
