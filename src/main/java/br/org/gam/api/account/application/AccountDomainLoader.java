package br.org.gam.api.account.application;

import br.org.gam.api.account.domain.Account;
import br.org.gam.api.account.domain.MyEmail;
import br.org.gam.api.account.persistence.AccountRepository;
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
                .orElseThrow(() -> new AccountNotFoundException("Could not find account with id " + id));
    }

    public Account requiredByEmail(MyEmail email) {
        return accountRepo.findByEmail(email)
                .map(accountMapper::entityToDomain)
                .orElseThrow(() -> new AccountNotFoundException("Could not find account with email " + email));
    }
}
