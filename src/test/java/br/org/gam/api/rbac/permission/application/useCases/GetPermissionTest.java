package br.org.gam.api.rbac.permission.application.useCases;

import br.org.gam.api.rbac.permission.application.PermissionMapper;
import br.org.gam.api.rbac.permission.application.PermissionRDTO;
import br.org.gam.api.rbac.permission.application.PermissionEntityLoader;
import br.org.gam.api.rbac.permission.persistence.PermissionEntity;
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
@DisplayName("Get Permission Use Case")
class GetPermissionTest {

    @Mock
    private PermissionEntityLoader getPermissionInstance;

    @Mock
    private PermissionMapper permissionMapper;

    @InjectMocks
    private GetPermission getPermission;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - existing permission id -> permission response")
        void existingPermissionIdShouldReturnPermissionResponse() {
            UUID id = UUID.randomUUID();
            PermissionEntity entity = new PermissionEntity();
            PermissionRDTO expectedResponse = new PermissionRDTO(
                    id,
                    "MEMBER_GET",
                    "View members",
                    "View active members"
            );

            when(getPermissionInstance.requiredById(id)).thenReturn(entity);
            when(permissionMapper.entityToRDTO(entity)).thenReturn(expectedResponse);

            PermissionRDTO response = getPermission.byId(id);

            assertThat(response).isSameAs(expectedResponse);
            verify(getPermissionInstance).requiredById(id);
            verify(permissionMapper).entityToRDTO(entity);
        }

        @Test
        @DisplayName("EP - missing permission id -> not found error")
        void missingPermissionIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(getPermissionInstance.requiredById(id))
                    .thenThrow(NotFoundException.resource("Permission", id));

            assertThatThrownBy(() -> getPermission.byId(id))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Permission not found with identifier " + id);

            verifyNoInteractions(permissionMapper);
        }
    }
}
