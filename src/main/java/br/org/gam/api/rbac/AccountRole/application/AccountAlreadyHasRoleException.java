package br.org.gam.api.rbac.AccountRole.application;

import br.org.gam.api.rbac.AccountRole.domain.AccountRole;

public class AccountAlreadyHasRoleException extends RuntimeException {
    public AccountAlreadyHasRoleException(String message) {
        super(message);
    }
}
