package br.org.gam.api.account.application.useCases.GetAccount;

import br.org.gam.api.account.application.AccountRDTO;
import java.util.UUID;

public interface GetAccount {
    AccountRDTO byId(UUID id);
}
