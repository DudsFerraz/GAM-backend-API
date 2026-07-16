package br.org.gam.api.account.application.useCases.getCurrentAccountContext;

import br.org.gam.api.account.application.AccountEntityLoader;
import br.org.gam.api.account.application.AccountMapper;
import br.org.gam.api.account.application.AccountRDTO;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.security.SecurityUtils;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetCurrentAccountContext {

    private final AccountEntityLoader accountEntityLoader;
    private final AccountMapper accountMapper;
    private final SecurityUtils securityUtils;

    public GetCurrentAccountContext(
            AccountEntityLoader accountEntityLoader,
            AccountMapper accountMapper,
            SecurityUtils securityUtils
    ) {
        this.accountEntityLoader = accountEntityLoader;
        this.accountMapper = accountMapper;
        this.securityUtils = securityUtils;
    }

    @Transactional(readOnly = true)
    public CurrentAccountContextRDTO get(UUID accountId) {
        AccountEntity accountEntity = accountEntityLoader.requiredById(accountId);
        AccountRDTO account = accountMapper.entityToRDTO(accountEntity);

        return new CurrentAccountContextRDTO(
                account.id(),
                account.email(),
                account.displayName(),
                account.roles().roles(),
                securityUtils.getLoggedUserAuthorities()
        );
    }
}
