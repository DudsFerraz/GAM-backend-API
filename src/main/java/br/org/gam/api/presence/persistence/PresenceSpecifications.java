package br.org.gam.api.presence.persistence;

import jakarta.persistence.criteria.JoinType;
import java.util.Locale;
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

    public static Specification<PresenceEntity> memberNameContains(String name) {
        return (root, query, builder) -> {
            String literalName = name.toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            String pattern = "%" + literalName + "%";
            return builder.or(
                    builder.like(
                            builder.lower(root.get("member").get("name").get("firstName")),
                            pattern,
                            '\\'
                    ),
                    builder.like(
                            builder.lower(root.get("member").get("name").get("surname")),
                            pattern,
                            '\\'
                    )
            );
        };
    }
}
