package br.org.gam.api.rbac.AccountRole.application;

public class AccountAlreadyHasRoleException extends RuntimeException {
    public AccountAlreadyHasRoleException(String message) {
        super(message);
    }
}
