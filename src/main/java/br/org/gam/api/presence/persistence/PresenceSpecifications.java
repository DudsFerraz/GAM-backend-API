package br.org.gam.api.presence.persistence;

import jakarta.persistence.criteria.JoinType;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public class PresenceSpecifications {
    public static Specification<PresenceEntity> fetchEvent() {
        return (root, query, builder) -> {
            if (Long.class != query.getResultType()) {
                root.fetch("event", JoinType.LEFT);
            }
            return null;
        };
    }

    public static Specification<PresenceEntity> fetchMember() {
        return (root, query, builder) -> {
            if (Long.class != query.getResultType()) {
                root.fetch("member", JoinType.LEFT);
            }
            return null;
        };
    }

    public static Specification<PresenceEntity> filterByMemberId(UUID memberId) {
        return (root, query, builder) -> builder.equal(root.get("member").get("id"), memberId);
    }

    public static Specification<PresenceEntity> filterByEventId(UUID eventId) {
        return (root, query, builder) -> builder.equal(root.get("event").get("id"), eventId);
    }
}
