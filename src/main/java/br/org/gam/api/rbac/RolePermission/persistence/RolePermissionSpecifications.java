package br.org.gam.api.rbac.RolePermission.persistence;

import jakarta.persistence.criteria.JoinType;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public class RolePermissionSpecifications {
    public static Specification<RolePermissionEntity> fetchRole() {
        return (root, query, builder) -> {
            if (Long.class != query.getResultType()) {
                root.fetch("role", JoinType.LEFT);
            }
            return null;
        };
    }

    public static Specification<RolePermissionEntity> fetchPermission() {
        return (root, query, builder) -> {
            if (Long.class != query.getResultType()) {
                root.fetch("permission", JoinType.LEFT);
            }
            return null;
        };
    }

    public static Specification<RolePermissionEntity> filterByRoleId(UUID roleId) {
        return (root, query, builder) -> builder.equal(root.get("role").get("id"), roleId);
    }
}
