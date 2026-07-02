package br.org.gam.api.rbac.AccountRole.application;

import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleRepository;
import org.springframework.stereotype.Service;

@Service
public class AccountRoleEntityLoader {

    private final AccountRoleRepository accountRoleRepo;

    public AccountRoleEntityLoader(AccountRoleRepository accountRoleRepo) {
        this.accountRoleRepo = accountRoleRepo;
    }

    public AccountRoleEntity requiredByDTO(AccountRoleDTO dto) {
        return accountRoleRepo.findByAccount_IdAndRole_Id(dto.accountId(), dto.roleId())
                .orElseThrow(() -> new AccountRoleNotFoundException(
                        String.format("Account with id: %s does not have role with id: %s", dto.accountId(), dto.roleId())
                ));
    }
}
