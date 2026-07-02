package br.org.gam.api.account.application.useCases;

import br.org.gam.api.account.application.AccountMapper;
import br.org.gam.api.account.application.AccountRDTO;
import br.org.gam.api.account.application.search.AccountSearchFilterConverter;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.account.persistence.AccountRepository;
import br.org.gam.api.shared.specification.SearchDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchAccounts {

    private final AccountRepository accountRepo;
    private final AccountMapper accountMapper;
    private final AccountSearchFilterConverter searchFilterConverter;

    public SearchAccounts(AccountRepository accountRepo, AccountMapper accountMapper, AccountSearchFilterConverter searchFilterConverter) {
        this.accountRepo = accountRepo;
        this.accountMapper = accountMapper;
        this.searchFilterConverter = searchFilterConverter;
    }

    @Transactional(readOnly = true)
    public Page<AccountRDTO> search(SearchDTO searchDTO, Pageable pageable) {
        Specification<AccountEntity> spec = searchFilterConverter.convert(searchDTO);

        Page<AccountEntity> entitiesPage = accountRepo.findAll(spec, pageable);

        return entitiesPage
                .map(accountMapper::entityToRDTO);
    }

}
