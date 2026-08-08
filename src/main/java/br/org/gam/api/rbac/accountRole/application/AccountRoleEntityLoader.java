package br.org.gam.api.rbac.accountRole.application;

import br.org.gam.api.rbac.accountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleRepository;
import br.org.gam.api.shared.exception.NotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AccountRoleEntityLoader {

    private final AccountRoleRepository accountRoleRepo;

    public AccountRoleEntityLoader(AccountRoleRepository accountRoleRepo) {
        this.accountRoleRepo = accountRoleRepo;
    }

    public AccountRoleEntity requiredByDTO(AccountRoleDTO dto) {
        return accountRoleRepo.findByAccount_IdAndRole_Id(dto.accountId(), dto.roleId())
                .orElseThrow(() -> NotFoundException.resource(
                        "AccountRole",
                        "%s:%s".formatted(dto.accountId(), dto.roleId())
                ));
    }
}
