package br.org.gam.api.account.application.useCases;

import br.org.gam.api.account.application.AccountMapper;
import br.org.gam.api.account.application.AccountRDTO;
import br.org.gam.api.account.application.AccountEntityLoader;
import br.org.gam.api.account.persistence.AccountEntity;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetAccount {

    private final AccountMapper accountMapper;
    private final AccountEntityLoader getAccountInstance;

    public GetAccount(AccountMapper accountMapper, AccountEntityLoader getAccountInstance) {
        this.accountMapper = accountMapper;
        this.getAccountInstance = getAccountInstance;
    }

    @Transactional(readOnly = true)
    public AccountRDTO byId(UUID id) {

        AccountEntity accountEntity = getAccountInstance.requiredById(id);
        return accountMapper.entityToRDTO(accountEntity);
    }

}
