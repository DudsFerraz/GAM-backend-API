package br.org.gam.api.account.application;

import br.org.gam.api.account.domain.MyEmail;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.account.persistence.AccountRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AccountEntityLoader {

    private final AccountRepository accountRepo;

    public AccountEntityLoader(AccountRepository accountRepo) {
        this.accountRepo = accountRepo;
    }

    public AccountEntity requiredById(UUID id) {
        return accountRepo.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Could not find account with id " + id));
    }

    public AccountEntity requiredByEmail(MyEmail email) {
        return accountRepo.findByEmail(email)
                .orElseThrow(() -> new AccountNotFoundException("Could not find account with email " + email));
    }
}
