package br.org.gam.api.member.persistence;

import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

public class MemberSpecifications {
    public static Specification<MemberEntity> fetchAccount() {

        return (root, query, builder) -> {
            if (Long.class != query.getResultType()) {
                root.fetch("account", JoinType.LEFT);
            }
            return null;
        };
    }


}
