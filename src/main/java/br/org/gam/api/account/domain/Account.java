package br.org.gam.api.account.domain;

import br.org.gam.api.shared.persistence.UUIDGenerator;
import java.util.Objects;
import java.util.UUID;

public class Account {
    private UUID id;
    private MyEmail email;
    private String passwordHash;
    private String displayName;

    /**
     * @deprecated <b>ESTE CONSTRUTOR É EXCLUSIVO PARA USO INTERNO E JPA/MapStruct.</b>
     * <br> <br>
     * <b> Use o método fábrica {@link #register(MyEmail email, String passwordHash, String displayName)}.
     */
    @Deprecated
    public Account(UUID id, MyEmail email, String passwordHash, String displayName) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    public static Account register(MyEmail email, String passwordHash, String displayName) {
        Objects.requireNonNull(email, "Email cannot be null.");
        Objects.requireNonNull(passwordHash, "Password hash cannot be null.");
        Objects.requireNonNull(displayName, "Display name cannot be null.");
        if (passwordHash.isBlank()) throw new IllegalArgumentException("Password hash cannot be blank.");
        if (displayName.isBlank()) throw new IllegalArgumentException("Display name cannot be blank.");

        UUID id = UUIDGenerator.generateUUIDV7();

        displayName = displayName.trim();

        return new Account(id, email, passwordHash, displayName);
    }

    public UUID getId() {
        return id;
    }

    public MyEmail getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }
}
