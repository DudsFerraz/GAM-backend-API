package br.org.gam.api.rbac.Permission.application.useCases.GetPermission;

import br.org.gam.api.rbac.Permission.application.PermissionMapper;
import br.org.gam.api.rbac.Permission.application.PermissionNotFoundException;
import br.org.gam.api.rbac.Permission.application.PermissionRDTO;
import br.org.gam.api.rbac.Permission.application.useCases.GetPermissionInstance.GetPermissionInstance;
import br.org.gam.api.rbac.Permission.domain.Permission;
import br.org.gam.api.rbac.Permission.persistence.PermissionEntity;
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
@DisplayName("Get Permission Use Case")
class SpringGetPermissionTest {

    @Mock
    private GetPermissionInstance getPermissionInstance;

    @Mock
    private PermissionMapper permissionMapper;

    @InjectMocks
    private SpringGetPermission getPermission;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - existing permission id -> permission response")
        void existingPermissionIdShouldReturnPermissionResponse() {
            UUID id = UUID.randomUUID();
            PermissionEntity entity = new PermissionEntity();
            PermissionRDTO expectedResponse = new PermissionRDTO(id, "MEMBER_GET", "View active members");

            when(getPermissionInstance.entityById(id)).thenReturn(entity);
            when(permissionMapper.entityToPermissionRDTO(entity)).thenReturn(expectedResponse);

            PermissionRDTO response = getPermission.byId(id);

            assertThat(response).isSameAs(expectedResponse);
            verify(getPermissionInstance).entityById(id);
            verify(permissionMapper).entityToPermissionRDTO(entity);
        }

        @Test
        @DisplayName("EP - missing permission id -> not found error")
        void missingPermissionIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(getPermissionInstance.entityById(id))
                    .thenThrow(new PermissionNotFoundException("Could not find permission with id " + id));

            assertThatThrownBy(() -> getPermission.byId(id))
                    .isInstanceOf(PermissionNotFoundException.class)
                    .hasMessage("Could not find permission with id " + id);

            verifyNoInteractions(permissionMapper);
        }
    }
}
