package br.org.gam.api.member.solicitation.persistence;

import jakarta.persistence.criteria.JoinType;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class MembershipSolicitationSpecifications {
    private MembershipSolicitationSpecifications() {
    }

    public static Specification<MembershipSolicitationEntity> belongsToAccount(UUID accountId) {
        return (root, query, builder) -> builder.equal(root.get("account").get("id"), accountId);
    }

    public static Specification<MembershipSolicitationEntity> fetchResponseRelations() {
        return (root, query, builder) -> {
            if (Long.class != query.getResultType()) {
                root.fetch("member", JoinType.LEFT);
            }
            return builder.conjunction();
        };
    }
}
