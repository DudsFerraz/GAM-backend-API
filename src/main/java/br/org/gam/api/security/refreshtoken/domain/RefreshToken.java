package br.org.gam.api.security.refreshtoken.domain;

import br.org.gam.api.account.domain.Account;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class RefreshToken {
    private UUID id;
    private UUID token;
    private Instant expiryDate;
    private Account account;

    /**
     * @deprecated <b>ESTE CONSTRUTOR É EXCLUSIVO PARA USO INTERNO E JPA/MapStruct.</b>
     * <br> <br>
     * <b> Use o método fábrica {@link #register(UUID token, Instant expiryDate, Account account)}.
     */
    public RefreshToken(UUID id, UUID token, Instant expiryDate, Account account) {
        this.id = id;
        this.token = token;
        this.expiryDate = expiryDate;
        this.account = account;
    }

    public static RefreshToken register(UUID token, Instant expiryDate, Account account) {
        Objects.requireNonNull(token, "token cannot be null");
        Objects.requireNonNull(account, "account cannot be null");
        Objects.requireNonNull(expiryDate, "expiryDate cannot be null");

        UUID id = UUIDGenerator.generateUUIDV7();
        return new RefreshToken(id, token, expiryDate, account);
    }

    public UUID getId() {
        return id;
    }

    public UUID getToken() {
        return token;
    }

    public Instant getExpiryDate() {
        return expiryDate;
    }

    public Account getAccount() {
        return account;
    }
}
