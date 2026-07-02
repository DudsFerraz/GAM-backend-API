package br.org.gam.api.rbac.AccountRole.application.useCases.GetAccountRoleInstance;

import br.org.gam.api.account.domain.Account;
import br.org.gam.api.rbac.AccountRole.application.AccountRoleDTO;
import br.org.gam.api.rbac.AccountRole.application.AccountRoleNotFoundException;
import br.org.gam.api.rbac.AccountRole.domain.AccountRole;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleRepository;
import org.springframework.stereotype.Service;

@Service
public class SpringGetAccountRoleInstance implements GetAccountRoleInstance {
    private final AccountRoleRepository accountRoleRepo;

    public SpringGetAccountRoleInstance(AccountRoleRepository accountRoleRepo) {
        this.accountRoleRepo = accountRoleRepo;
    }

    @Override
    public AccountRoleEntity entityByDTO(AccountRoleDTO dto) {

        return accountRoleRepo.findByAccount_IdAndRole_Id(dto.accountId(), dto.roleId())
                .orElseThrow(() ->  new AccountRoleNotFoundException(
                        String.format("Account with id: %s does not have role with id: %s",  dto.accountId(), dto.roleId())
                ));
    }
}
