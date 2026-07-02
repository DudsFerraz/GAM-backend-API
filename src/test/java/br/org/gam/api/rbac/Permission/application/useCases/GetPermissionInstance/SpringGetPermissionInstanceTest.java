package br.org.gam.api.rbac.Permission.application.useCases.GetPermissionInstance;

import br.org.gam.api.rbac.Permission.application.PermissionMapper;
import br.org.gam.api.rbac.Permission.application.PermissionNotFoundException;
import br.org.gam.api.rbac.Permission.domain.Permission;
import br.org.gam.api.rbac.Permission.persistence.PermissionEntity;
import br.org.gam.api.rbac.Permission.persistence.PermissionRepository;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.Optional;
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
@DisplayName("Get Permission Instance Use Case")
class SpringGetPermissionInstanceTest {

    @Mock
    private PermissionRepository permissionRepo;

    @Mock
    private PermissionMapper permissionMapper;

    @InjectMocks
    private SpringGetPermissionInstance getPermissionInstance;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - existing id -> domain permission")
        void existingIdShouldReturnDomainPermission() {
            UUID id = UUID.randomUUID();
            PermissionEntity entity = new PermissionEntity();
            Permission domain = Permission.register("MEMBER_GET", "View active members");

            when(permissionRepo.findById(id)).thenReturn(Optional.of(entity));
            when(permissionMapper.entityToDomain(entity)).thenReturn(domain);

            Permission result = getPermissionInstance.domainById(id);

            assertThat(result).isSameAs(domain);
            verify(permissionMapper).entityToDomain(entity);
        }

        @Test
        @DisplayName("EP - existing id -> permission entity")
        void existingIdShouldReturnPermissionEntity() {
            UUID id = UUID.randomUUID();
            PermissionEntity entity = new PermissionEntity();

            when(permissionRepo.findById(id)).thenReturn(Optional.of(entity));

            PermissionEntity result = getPermissionInstance.entityById(id);

            assertThat(result).isSameAs(entity);
            verifyNoInteractions(permissionMapper);
        }

        @Test
        @DisplayName("EP - missing id -> not found error")
        void missingIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(permissionRepo.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getPermissionInstance.entityById(id))
                    .isInstanceOf(PermissionNotFoundException.class)
                    .hasMessage("Could not find permission with id " + id);

            verifyNoInteractions(permissionMapper);
        }
    }

    @Nested
    @StructuralTest
    @DisplayName("Structural")
    class Structural {

        @Test
        @DisplayName("missing id for domain lookup -> not found error")
        void missingIdForDomainLookupShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(permissionRepo.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getPermissionInstance.domainById(id))
                    .isInstanceOf(PermissionNotFoundException.class)
                    .hasMessage("Could not find permission with id " + id);

            verifyNoInteractions(permissionMapper);
        }
    }
}
