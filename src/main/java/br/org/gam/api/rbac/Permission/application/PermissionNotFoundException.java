package br.org.gam.api.rbac.Permission.application;

import br.org.gam.api.rbac.Permission.domain.Permission;

public class PermissionNotFoundException extends RuntimeException {
    public PermissionNotFoundException(String message) {
        super(message);
    }
}
