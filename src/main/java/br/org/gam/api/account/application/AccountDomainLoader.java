package br.org.gam.api.account.application;

import br.org.gam.api.account.domain.Account;
import br.org.gam.api.account.domain.MyEmail;
import br.org.gam.api.account.persistence.AccountRepository;
import br.org.gam.api.shared.exception.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AccountDomainLoader {

    private final AccountRepository accountRepo;
    private final AccountMapper accountMapper;

    public AccountDomainLoader(AccountRepository accountRepo, AccountMapper accountMapper) {
        this.accountRepo = accountRepo;
        this.accountMapper = accountMapper;
    }

    public Account requiredById(UUID id) {
        return accountRepo.findById(id)
                .map(accountMapper::entityToDomain)
                .orElseThrow(() -> NotFoundException.resource("Account", id));
    }

    public Account requiredByEmail(MyEmail email) {
        return accountRepo.findByEmail(email)
                .map(accountMapper::entityToDomain)
                .orElseThrow(() -> NotFoundException.resource("Account", email));
    }
}
