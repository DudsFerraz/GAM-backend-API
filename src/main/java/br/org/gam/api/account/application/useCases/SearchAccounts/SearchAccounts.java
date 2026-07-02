package br.org.gam.api.account.application.useCases.SearchAccounts;

import br.org.gam.api.account.application.AccountRDTO;
import br.org.gam.api.shared.specification.SpecificationFilter;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SearchAccounts {

    public Page<AccountRDTO> search(List<SpecificationFilter> filters, Pageable pageable);
}
