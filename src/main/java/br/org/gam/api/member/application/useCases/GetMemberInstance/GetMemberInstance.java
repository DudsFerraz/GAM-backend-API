package br.org.gam.api.member.application.useCases.GetMemberInstance;

import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.persistence.MemberEntity;
import java.util.Set;
import java.util.UUID;

public interface GetMemberInstance {
    Member domainById(UUID id);
    MemberEntity entityById(UUID id);
    Set<Member> domainsById(Set<UUID> ids);
}
