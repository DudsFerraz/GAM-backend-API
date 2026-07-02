package br.org.gam.api.event.persistence;

import jakarta.persistence.criteria.JoinType;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

public class EventSecuritySpecification {
    public static Specification<EventEntity> canGetEvent(Set<String> userAuthorities) {
        return (root, query, cb) -> {
            var isPublic = cb.isNull(root.get("requiredPermission"));
            if (userAuthorities == null || userAuthorities.isEmpty()) {
                return isPublic;
            }

            var permissionJoin = root.join("requiredPermission", JoinType.LEFT);
            var hasAuthority = permissionJoin.get("name").in(userAuthorities);

            return cb.or(isPublic, hasAuthority);
        };
    }
}
