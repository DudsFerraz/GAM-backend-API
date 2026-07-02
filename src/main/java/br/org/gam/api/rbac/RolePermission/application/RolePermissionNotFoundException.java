package br.org.gam.api.rbac.RolePermission.application;

import br.org.gam.api.rbac.RolePermission.domain.RolePermission;

public class RolePermissionNotFoundException extends RuntimeException {
    public RolePermissionNotFoundException(String message) {
        super(message);
    }
}
