package br.org.gam.api.member.persistence;

import br.org.gam.api.member.domain.MemberStatus;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

public class MemberSecuritySpecification {
    public static Specification<MemberEntity> canGetMember(Set<String> userAuthorities) {
        return (root, query, cb) -> {
            if (userAuthorities.contains("MEMBER_GET_NON_ACTIVE")) return cb.conjunction();

            return cb.equal(root.get("status"), MemberStatus.ACTIVE);
        };
    }
}
