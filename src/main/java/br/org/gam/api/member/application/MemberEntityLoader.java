package br.org.gam.api.member.application;

import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.member.persistence.MemberRepository;
import br.org.gam.api.shared.exception.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MemberEntityLoader {

    private final MemberRepository memberRepo;

    public MemberEntityLoader(MemberRepository memberRepo) {
        this.memberRepo = memberRepo;
    }

    public MemberEntity requiredById(UUID id) {
        return memberRepo.findById(id)
                .orElseThrow(() -> NotFoundException.resource("Member", id));
    }
}
