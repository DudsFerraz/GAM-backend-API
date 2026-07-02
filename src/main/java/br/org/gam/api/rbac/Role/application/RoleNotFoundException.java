package br.org.gam.api.rbac.Role.application;

import br.org.gam.api.rbac.Role.domain.Role;

public class RoleNotFoundException extends RuntimeException {
    public RoleNotFoundException(String message) {
        super(message);
    }
}
