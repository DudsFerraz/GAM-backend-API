package br.org.gam.api.member.application;

import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.persistence.MemberRepository;
import br.org.gam.api.shared.exception.NotFoundException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MemberDomainLoader {

    private final MemberRepository memberRepo;
    private final MemberMapper memberMapper;

    public MemberDomainLoader(MemberRepository memberRepo, MemberMapper memberMapper) {
        this.memberRepo = memberRepo;
        this.memberMapper = memberMapper;
    }

    public Member requiredById(UUID id) {
        return memberRepo.findById(id)
                .map(memberMapper::entityToDomain)
                .orElseThrow(() -> NotFoundException.resource("Member", id));
    }

    public Set<Member> requiredByIds(Set<UUID> ids) {
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
