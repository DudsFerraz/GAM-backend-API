package br.org.gam.api.rbac.AccountRole.application;

import br.org.gam.api.account.application.AccountMapper;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.Role.application.RoleMapper;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {AccountMapper.class, RoleMapper.class})
public interface AccountRoleMapper {
    AccountRoleRDTO entityToAccountRoleRDTO(AccountRoleEntity accountRoleEntity);
}
