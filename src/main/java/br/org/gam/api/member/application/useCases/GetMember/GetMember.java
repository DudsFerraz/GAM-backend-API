package br.org.gam.api.member.application.useCases.GetMember;

import br.org.gam.api.member.application.MemberRDTO;
import java.util.UUID;

public interface GetMember {
    public MemberRDTO byId(UUID id);
}
