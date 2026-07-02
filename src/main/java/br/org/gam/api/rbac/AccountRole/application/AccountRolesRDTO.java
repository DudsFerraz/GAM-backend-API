package br.org.gam.api.rbac.AccountRole.application;

import br.org.gam.api.rbac.Role.application.RoleRDTO;
import java.util.ArrayList;
import java.util.List;

public record AccountRolesRDTO(
        List<RoleRDTO> roles
) {
    public AccountRolesRDTO() {
        this(new ArrayList<>());
    }
}
