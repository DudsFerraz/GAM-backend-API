package br.org.gam.api.rbac.AccountRole.application;

import br.org.gam.api.rbac.AccountRole.domain.AccountRole;

public class AccountRoleNotFoundException extends RuntimeException {
    public AccountRoleNotFoundException(String message) {
        super(message);
    }
}
