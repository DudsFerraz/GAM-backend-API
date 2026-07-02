package br.org.gam.api.rbac.AccountRole.application;

import br.org.gam.api.account.application.AccountMapper;
import br.org.gam.api.rbac.AccountRole.domain.AccountRole;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.Role.application.RoleMapper;
import br.org.gam.api.shared.auditing.IgnoreJunctionAuditFields;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {AccountMapper.class, RoleMapper.class})
public interface AccountRoleMapper {
    @IgnoreJunctionAuditFields
    AccountRoleEntity domainToEntity(AccountRole newAccountRole);
    AccountRoleRDTO entityToAccountRoleRDTO(AccountRoleEntity accountRoleEntity);
}
