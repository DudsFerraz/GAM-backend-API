package br.org.gam.api.rbac.Role.domain;

import br.org.gam.api.shared.persistence.UUIDGenerator;
import java.util.Objects;
import java.util.UUID;

public class Role {
    private UUID id;
    private String name;
    private String description;

    /**
     * @deprecated <b>ESTE CONSTRUTOR É EXCLUSIVO PARA USO INTERNO E JPA/MapStruct.</b>
     * <br> <br>
     * <b> Use o método fábrica {@link #register(String name, String description)}.
     */
    @Deprecated
    public Role(UUID id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public static Role register(String name, String description) {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        if (name.isBlank()) throw new IllegalArgumentException("name cannot be blank");
        if (description.isBlank()) throw new IllegalArgumentException("description cannot be blank");

        name = name.trim();
        description = description.trim();

        UUID id = UUIDGenerator.generateUUIDV7();

        return new Role(id, name, description);
    }


    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
