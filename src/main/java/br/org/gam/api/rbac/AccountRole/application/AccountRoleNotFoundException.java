package br.org.gam.api.rbac.AccountRole.application;

public class AccountRoleNotFoundException extends RuntimeException {
    public AccountRoleNotFoundException(String message) {
        super(message);
    }
}
