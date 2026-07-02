package br.org.gam.api.account.application.useCases.GetAccount;

import br.org.gam.api.account.application.AccountMapper;
import br.org.gam.api.account.application.AccountRDTO;
import br.org.gam.api.account.application.useCases.GetAccountInstance.GetAccountInstance;
import br.org.gam.api.account.persistence.AccountEntity;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetAccount {

    private final AccountMapper accountMapper;
    private final GetAccountInstance getAccountInstance;

    public GetAccount(AccountMapper accountMapper, GetAccountInstance getAccountInstance) {
        this.accountMapper = accountMapper;
        this.getAccountInstance = getAccountInstance;
    }

    @Transactional(readOnly = true)
    public AccountRDTO byId(UUID id) {

        AccountEntity accountEntity = getAccountInstance.entityById(id);
        return accountMapper.entityToAccountRDTO(accountEntity);
    }

}
