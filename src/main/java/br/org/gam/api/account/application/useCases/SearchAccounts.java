package br.org.gam.api.account.application.useCases;

import br.org.gam.api.account.application.AccountMapper;
import br.org.gam.api.account.application.AccountRDTO;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.account.persistence.AccountRepository;
import br.org.gam.api.shared.specification.SpecificationBuilder;
import br.org.gam.api.shared.specification.SpecificationFilter;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchAccounts {

    private final AccountRepository accountRepo;
    private final AccountMapper accountMapper;

    public SearchAccounts(AccountRepository accountRepo, AccountMapper accountMapper) {
        this.accountRepo = accountRepo;
        this.accountMapper = accountMapper;
    }

    @Transactional(readOnly = true)
    public Page<AccountRDTO> search(List<SpecificationFilter> filters, Pageable pageable) {
        Specification<AccountEntity> spec = SpecificationBuilder.build(filters);

        Page<AccountEntity> entitiesPage = accountRepo.findAll(spec, pageable);

        return entitiesPage
                .map(accountMapper::entityToAccountRDTO);
    }

}
