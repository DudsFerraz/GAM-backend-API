package br.org.gam.api.rbac.role.application.useCases;

import br.org.gam.api.rbac.role.application.RoleMapper;
import br.org.gam.api.rbac.role.application.RoleRDTO;
import br.org.gam.api.rbac.role.application.RoleEntityLoader;
import br.org.gam.api.rbac.role.persistence.RoleEntity;
import br.org.gam.api.shared.exception.NotFoundException;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Get Role Use Case")
class GetRoleTest {

    @Mock
    private RoleEntityLoader getRoleInstance;

    @Mock
    private RoleMapper roleMapper;

    @InjectMocks
    private GetRole getRole;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - existing role id -> role response")
        void existingRoleIdShouldReturnRoleResponse() {
            UUID id = UUID.randomUUID();
            RoleEntity entity = new RoleEntity();
            RoleRDTO expectedResponse = new RoleRDTO(id, "ADMIN", "System administrator");

            when(getRoleInstance.requiredById(id)).thenReturn(entity);
            when(roleMapper.entityToRDTO(entity)).thenReturn(expectedResponse);

            RoleRDTO response = getRole.byId(id);

            assertThat(response).isSameAs(expectedResponse);
            verify(getRoleInstance).requiredById(id);
            verify(roleMapper).entityToRDTO(entity);
        }

        @Test
        @DisplayName("EP - missing role id -> not found error")
        void missingRoleIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(getRoleInstance.requiredById(id))
                    .thenThrow(NotFoundException.resource("Role", id));

            assertThatThrownBy(() -> getRole.byId(id))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Role not found with identifier " + id);

            verifyNoInteractions(roleMapper);
        }
    }
}
