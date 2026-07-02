package br.org.gam.api.member.application.useCases;

import br.org.gam.api.member.application.MemberMapper;
import br.org.gam.api.member.application.MemberRDTO;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.member.persistence.MemberRepository;
import br.org.gam.api.member.persistence.MemberSecuritySpecification;
import br.org.gam.api.member.persistence.MemberSpecifications;
import br.org.gam.api.security.SecurityUtils;
import br.org.gam.api.shared.specification.SpecificationBuilder;
import br.org.gam.api.shared.specification.SpecificationFilter;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class SearchMembers {

    private final MemberRepository memberRepo;
    private final MemberMapper memberMapper;
    private final SecurityUtils securityUtils;

    public SearchMembers(MemberRepository memberRepo, MemberMapper memberMapper, SecurityUtils securityUtils) {
        this.memberRepo = memberRepo;
        this.memberMapper = memberMapper;
        this.securityUtils = securityUtils;
    }
    public Page<MemberRDTO> search(List<SpecificationFilter> filters, Pageable pageable) {
        Set<String> authorities = securityUtils.getLoggedUserAuthorities();
        Specification<MemberEntity> securityFilter = MemberSecuritySpecification.canGetMember(authorities);
        Specification<MemberEntity> searchFilters = SpecificationBuilder.build(filters);
        Specification<MemberEntity> spec = securityFilter.and(searchFilters).and(MemberSpecifications.fetchAccount());

        return memberRepo.findAll(spec, pageable)
                .map(memberMapper::entityToRDTO);
    }


}
