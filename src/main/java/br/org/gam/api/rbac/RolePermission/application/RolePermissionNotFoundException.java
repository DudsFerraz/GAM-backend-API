package br.org.gam.api.rbac.RolePermission.application;

public class RolePermissionNotFoundException extends RuntimeException {
    public RolePermissionNotFoundException(String message) {
        super(message);
    }
}
