package br.org.gam.api.member.application.useCases.GetMemberInstance;

import br.org.gam.api.member.application.MemberMapper;
import br.org.gam.api.member.application.MemberNotFoundException;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.member.persistence.MemberRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetMemberInstance {

    private final MemberRepository memberRepo;
    private final MemberMapper memberMapper;

    public GetMemberInstance(MemberRepository memberRepo, MemberMapper memberMapper) {
        this.memberRepo = memberRepo;
        this.memberMapper = memberMapper;
    }
    public Member domainById(UUID id) {
        return memberRepo.findById(id)
                .map(memberMapper::entityToDomain)
                .orElseThrow(() -> new MemberNotFoundException("Could not find member with id " + id));
    }
    public MemberEntity entityById(UUID id) {
        return memberRepo.findById(id)
                .orElseThrow(() -> new MemberNotFoundException("Could not find member with id " + id));
    }
    public Set<Member> domainsById(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) return new HashSet<>();

        Set<UUID> safeIds = ids.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        return memberRepo.findAllById(safeIds)
                .stream()
                .map(memberMapper::entityToDomain)
                .collect(Collectors.toSet());
    }
}
