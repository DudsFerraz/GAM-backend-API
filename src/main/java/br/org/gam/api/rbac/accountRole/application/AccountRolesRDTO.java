package br.org.gam.api.rbac.accountRole.application;

import br.org.gam.api.rbac.role.application.RoleRDTO;
import java.util.ArrayList;
import java.util.List;

public record AccountRolesRDTO(
        List<RoleRDTO> roles
) {
    public AccountRolesRDTO() {
        this(new ArrayList<>());
    }
}
