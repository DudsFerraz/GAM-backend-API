package br.org.gam.api.rbac.Role.application.useCases.GetRole;

import br.org.gam.api.rbac.Role.application.RoleRDTO;
import java.util.UUID;

public interface GetRole {
    RoleRDTO byId(UUID id);
}
