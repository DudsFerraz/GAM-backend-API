package br.org.gam.api.rbac.accountRole.application.useCases;

import br.org.gam.api.rbac.accountRole.application.AccountRoleMapper;
import br.org.gam.api.rbac.accountRole.application.AccountRoleRDTO;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleRepository;
import br.org.gam.api.shared.exception.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetAccountRoleAssignment {
    private final AccountRoleRepository accountRoleRepo;
    private final AccountRoleMapper mapper;

    public GetAccountRoleAssignment(AccountRoleRepository accountRoleRepo, AccountRoleMapper mapper) {
        this.accountRoleRepo = accountRoleRepo;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public AccountRoleRDTO get(UUID accountId, UUID assignmentId) {
        AccountRoleEntity assignment = accountRoleRepo.findActiveAssignment(accountId, assignmentId)
                .orElseThrow(() -> NotFoundException.resource("AccountRole", assignmentId));
        return mapper.entityToRDTO(assignment);
    }
}
