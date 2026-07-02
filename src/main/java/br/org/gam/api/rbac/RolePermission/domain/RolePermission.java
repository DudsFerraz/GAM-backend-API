package br.org.gam.api.rbac.RolePermission.domain;

import br.org.gam.api.rbac.Permission.domain.Permission;
import br.org.gam.api.rbac.Permission.persistence.PermissionEntity;
import br.org.gam.api.rbac.Role.domain.Role;
import br.org.gam.api.rbac.Role.persistence.RoleEntity;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import java.util.Objects;
import java.util.UUID;

public class RolePermission {
    private UUID id;
    private RoleEntity role;
    private PermissionEntity permission;

    /**
     * @deprecated <b>ESTE CONSTRUTOR É EXCLUSIVO PARA USO INTERNO E JPA/MapStruct.</b>
     * <br> <br>
     * <b> Use o método fábrica {@link #register(RoleEntity role, PermissionEntity permission)}.
     */
    @Deprecated
    public RolePermission(UUID id, RoleEntity role, PermissionEntity permission) {
        this.id = id;
        this.role = role;
        this.permission = permission;
    }

    public static RolePermission register(RoleEntity role, PermissionEntity permission) {
        Objects.requireNonNull(role, "role cannot be null");
        Objects.requireNonNull(permission, "permission cannot be null");

        UUID id = UUIDGenerator.generateUUIDV7();

        return new RolePermission(id, role, permission);
    }

    public UUID getId() {
        return id;
    }

    public RoleEntity getRole() {
        return role;
    }

    public PermissionEntity getPermission() {
        return permission;
    }
}
