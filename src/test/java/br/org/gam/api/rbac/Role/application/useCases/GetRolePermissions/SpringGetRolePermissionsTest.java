package br.org.gam.api.rbac.Role.application.useCases.GetRolePermissions;

import br.org.gam.api.rbac.Permission.application.PermissionMapper;
import br.org.gam.api.rbac.Permission.application.PermissionRDTO;
import br.org.gam.api.rbac.Permission.persistence.PermissionEntity;
import br.org.gam.api.rbac.RolePermission.persistence.RolePermissionEntity;
import br.org.gam.api.rbac.RolePermission.persistence.RolePermissionRepository;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Get Role Permissions Use Case")
@SuppressWarnings("unchecked")
class SpringGetRolePermissionsTest {

    @Mock
    private RolePermissionRepository rolePermissionRepo;

    @Mock
    private PermissionMapper permissionMapper;

    @InjectMocks
    private SpringGetRolePermissions getRolePermissions;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - role with permissions -> mapped permissions response")
        void roleWithPermissionsShouldReturnMappedPermissionsResponse() {
            UUID roleId = UUID.randomUUID();
            PermissionEntity firstPermission = permissionEntity();
            PermissionEntity secondPermission = permissionEntity();
            RolePermissionEntity firstRolePermission = rolePermissionEntity(firstPermission);
            RolePermissionEntity secondRolePermission = rolePermissionEntity(secondPermission);
            PermissionRDTO firstResponse = response(UUID.randomUUID(), "MEMBER_GET");
            PermissionRDTO secondResponse = response(UUID.randomUUID(), "MEMBER_SEARCH");

            when(rolePermissionRepo.findAll(org.mockito.ArgumentMatchers.any(Specification.class)))
                    .thenReturn(List.of(firstRolePermission, secondRolePermission));
            when(permissionMapper.entityToPermissionRDTO(firstPermission)).thenReturn(firstResponse);
            when(permissionMapper.entityToPermissionRDTO(secondPermission)).thenReturn(secondResponse);

            GetRolePermissionsRDTO response = getRolePermissions.allById(roleId);

            assertThat(response.permissions()).containsExactly(firstResponse, secondResponse);

            ArgumentCaptor<Specification<RolePermissionEntity>> specificationCaptor = ArgumentCaptor.forClass(Specification.class);
            verify(rolePermissionRepo).findAll(specificationCaptor.capture());
            assertThat(specificationCaptor.getValue()).isNotNull();
            verify(permissionMapper).entityToPermissionRDTO(firstPermission);
            verify(permissionMapper).entityToPermissionRDTO(secondPermission);
        }
    }

    @Nested
    @StructuralTest
    @DisplayName("Structural")
    class Structural {

        @Test
        @DisplayName("role without permissions -> empty permissions response")
        void roleWithoutPermissionsShouldReturnEmptyPermissionsResponse() {
            UUID roleId = UUID.randomUUID();

            when(rolePermissionRepo.findAll(org.mockito.ArgumentMatchers.any(Specification.class))).thenReturn(List.of());

            GetRolePermissionsRDTO response = getRolePermissions.allById(roleId);

            assertThat(response.permissions()).isEmpty();
            verifyNoInteractions(permissionMapper);
        }
    }

    private static RolePermissionEntity rolePermissionEntity(PermissionEntity permission) {
        RolePermissionEntity rolePermission = new RolePermissionEntity();
        rolePermission.setPermission(permission);
        return rolePermission;
    }

    private static PermissionEntity permissionEntity() {
        PermissionEntity permission = new PermissionEntity();
        permission.setId(UUID.randomUUID());
        permission.setName("MEMBER_GET");
        permission.setDescription("View active members");
        return permission;
    }

    private static PermissionRDTO response(UUID id, String name) {
        return new PermissionRDTO(id, name, "Description");
    }
}
