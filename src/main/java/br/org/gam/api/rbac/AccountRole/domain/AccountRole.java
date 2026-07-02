package br.org.gam.api.rbac.AccountRole.domain;

import br.org.gam.api.account.domain.Account;
import br.org.gam.api.rbac.Role.domain.Role;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import java.util.Objects;
import java.util.UUID;

public class AccountRole {
    private UUID id;
    private Account account;
    private Role role;

    /**
     * @deprecated <b>ESTE CONSTRUTOR É EXCLUSIVO PARA USO INTERNO E JPA/MapStruct.</b>
     * <br> <br>
     * <b> Use o método fábrica {@link #register(Account account, Role role)}.
     */
    @Deprecated
    AccountRole(UUID id, Account account, Role role) {
        this.id = id;
        this.account = account;
        this.role = role;
    }

    public static AccountRole register(Account account, Role role) {
        Objects.requireNonNull(account, "Account cannot be null");
        Objects.requireNonNull(role, "Role cannot be null");

        UUID id = UUIDGenerator.generateUUIDV7();

        return new AccountRole(id, account, role);
    }

    public Role getRole() {
        return role;
    }

    public Account getAccount() {
        return account;
    }

    public UUID getId() {
        return id;
    }
}
