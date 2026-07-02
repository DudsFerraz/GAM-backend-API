package br.org.gam.api.account.application.useCases.GetAccountInstance;

import br.org.gam.api.account.domain.Account;
import br.org.gam.api.account.domain.MyEmail;
import br.org.gam.api.account.persistence.AccountEntity;
import java.util.UUID;

public interface GetAccountInstance {
    Account domainById(UUID id);
    AccountEntity entityById(UUID id);
    Account domainByEmail(MyEmail email);
    AccountEntity entityByEmail(MyEmail email);
}
