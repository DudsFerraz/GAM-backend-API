package br.org.gam.api.account.application;

import br.org.gam.api.account.domain.MyEmail;
import br.org.gam.api.rbac.AccountRole.application.AccountRolesRDTO;
import br.org.gam.api.rbac.AccountRole.domain.AccountRole;
import java.util.UUID;

public record AccountRDTO(
        UUID id,
        MyEmail email,
        String displayName,
        AccountRolesRDTO roles
) {
}
