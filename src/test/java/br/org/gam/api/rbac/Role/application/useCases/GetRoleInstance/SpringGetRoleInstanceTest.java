package br.org.gam.api.rbac.Role.application.useCases.GetRoleInstance;

import br.org.gam.api.rbac.Role.application.RoleMapper;
import br.org.gam.api.rbac.Role.application.RoleNotFoundException;
import br.org.gam.api.rbac.Role.domain.Role;
import br.org.gam.api.rbac.Role.persistence.RoleEntity;
import br.org.gam.api.rbac.Role.persistence.RoleRepository;
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
@DisplayName("Get Role Instance Use Case")
class SpringGetRoleInstanceTest {

    @Mock
    private RoleRepository roleRepo;

    @Mock
    private RoleMapper roleMapper;

    @InjectMocks
    private SpringGetRoleInstance getRoleInstance;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - existing id -> domain role")
        void existingIdShouldReturnDomainRole() {
            UUID id = UUID.randomUUID();
            RoleEntity entity = new RoleEntity();
            Role domain = Role.register("ADMIN", "System administrator");

            when(roleRepo.findById(id)).thenReturn(Optional.of(entity));
            when(roleMapper.entityToDomain(entity)).thenReturn(domain);

            Role result = getRoleInstance.domainById(id);

            assertThat(result).isSameAs(domain);
            verify(roleMapper).entityToDomain(entity);
        }

        @Test
        @DisplayName("EP - existing id -> role entity")
        void existingIdShouldReturnRoleEntity() {
            UUID id = UUID.randomUUID();
            RoleEntity entity = new RoleEntity();

            when(roleRepo.findById(id)).thenReturn(Optional.of(entity));

            RoleEntity result = getRoleInstance.entityById(id);

            assertThat(result).isSameAs(entity);
            verifyNoInteractions(roleMapper);
        }

        @Test
        @DisplayName("EP - missing id -> not found error")
        void missingIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(roleRepo.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getRoleInstance.entityById(id))
                    .isInstanceOf(RoleNotFoundException.class)
                    .hasMessage("Could not find role with id " + id);

            verifyNoInteractions(roleMapper);
        }

        @Test
        @DisplayName("EP - existing name -> role entity")
        void existingNameShouldReturnRoleEntity() {
            RoleEntity entity = new RoleEntity();

            when(roleRepo.findByName("ADMIN")).thenReturn(Optional.of(entity));

            RoleEntity result = getRoleInstance.entityByName("ADMIN");

            assertThat(result).isSameAs(entity);
            verifyNoInteractions(roleMapper);
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

            when(roleRepo.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getRoleInstance.domainById(id))
                    .isInstanceOf(RoleNotFoundException.class)
                    .hasMessage("Could not find role with id " + id);

            verifyNoInteractions(roleMapper);
        }

        @Test
        @DisplayName("missing name for entity lookup -> not found error")
        void missingNameForEntityLookupShouldReturnNotFoundError() {
            when(roleRepo.findByName("ADMIN")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getRoleInstance.entityByName("ADMIN"))
                    .isInstanceOf(RoleNotFoundException.class)
                    .hasMessage("Could not find role with name ADMIN");

            verifyNoInteractions(roleMapper);
        }
    }
}
