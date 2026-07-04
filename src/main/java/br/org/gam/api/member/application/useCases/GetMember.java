package br.org.gam.api.member.application.useCases;

import br.org.gam.api.member.application.MemberMapper;
import br.org.gam.api.member.application.MemberRDTO;
import br.org.gam.api.member.application.MemberSecurity;
import br.org.gam.api.member.application.MemberEntityLoader;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.shared.exception.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetMember {
    private final MemberEntityLoader getMemberInstance;
    private final MemberMapper memberMapper;
    private final MemberSecurity memberSecurity;

    public GetMember(MemberEntityLoader getMemberInstance, MemberMapper memberMapper, MemberSecurity memberSecurity) {
        this.getMemberInstance = getMemberInstance;
        this.memberMapper = memberMapper;
        this.memberSecurity = memberSecurity;
    }
    public MemberRDTO byId(UUID id) {
        MemberEntity memberEntity = getMemberInstance.requiredById(id);
        if(!memberSecurity.canGetMember(memberEntity)) throw NotFoundException.resource("Member", id);

        return memberMapper.entityToRDTO(memberEntity);
    }

}
