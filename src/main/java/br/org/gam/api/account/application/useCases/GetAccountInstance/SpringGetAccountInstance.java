package br.org.gam.api.account.application.useCases.GetAccountInstance;

import br.org.gam.api.account.application.AccountMapper;
import br.org.gam.api.account.application.AccountNotFoundException;
import br.org.gam.api.account.domain.Account;
import br.org.gam.api.account.domain.MyEmail;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.account.persistence.AccountRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SpringGetAccountInstance implements GetAccountInstance {

    private final AccountRepository accountRepo;
    private final AccountMapper accountMapper;

    public SpringGetAccountInstance(AccountRepository accountRepo, AccountMapper accountMapper) {
        this.accountRepo = accountRepo;
        this.accountMapper = accountMapper;
    }

    @Override
    public Account domainById(UUID id) {
        return accountRepo.findById(id)
                .map(accountMapper::entityToDomain)
                .orElseThrow(() -> new AccountNotFoundException("Could not find account with id " + id));

    }

    @Override
    public AccountEntity entityById(UUID id) {
        return accountRepo.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Could not find account with id " + id));
    }

    @Override
    public Account domainByEmail(MyEmail email) {
        return accountRepo.findByEmail(email)
                .map(accountMapper::entityToDomain)
                .orElseThrow(() -> new AccountNotFoundException("Could not find account with email " + email));
    }

    @Override
    public AccountEntity entityByEmail(MyEmail email) {
        return accountRepo.findByEmail(email)
                .orElseThrow(() -> new AccountNotFoundException("Could not find account with email " + email));
    }
}
