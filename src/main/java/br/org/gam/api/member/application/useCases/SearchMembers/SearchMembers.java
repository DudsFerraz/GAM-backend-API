package br.org.gam.api.member.application.useCases.SearchMembers;

import br.org.gam.api.member.application.MemberRDTO;
import br.org.gam.api.shared.specification.SpecificationFilter;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SearchMembers {
    Page<MemberRDTO> search(List<SpecificationFilter> filters, Pageable pageable);
}
