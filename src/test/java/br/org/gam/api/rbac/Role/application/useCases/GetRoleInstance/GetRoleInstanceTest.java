package br.org.gam.api.rbac.Role.application.useCases.GetRoleInstance;

import br.org.gam.api.rbac.Role.application.RoleNotFoundException;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Get Role Instance Use Case")
class GetRoleInstanceTest {

    @Mock
    private RoleRepository roleRepo;

    @InjectMocks
    private GetRoleInstance getRoleInstance;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - existing id -> role entity")
        void existingIdShouldReturnRoleEntity() {
            UUID id = UUID.randomUUID();
            RoleEntity entity = new RoleEntity();

            when(roleRepo.findById(id)).thenReturn(Optional.of(entity));

            RoleEntity result = getRoleInstance.entityById(id);

            assertThat(result).isSameAs(entity);
        }

        @Test
        @DisplayName("EP - missing id -> not found error")
        void missingIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(roleRepo.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getRoleInstance.entityById(id))
                    .isInstanceOf(RoleNotFoundException.class)
                    .hasMessage("Could not find role with id " + id);

        }

        @Test
        @DisplayName("EP - existing name -> role entity")
        void existingNameShouldReturnRoleEntity() {
            RoleEntity entity = new RoleEntity();

            when(roleRepo.findByName("ADMIN")).thenReturn(Optional.of(entity));

            RoleEntity result = getRoleInstance.entityByName("ADMIN");

            assertThat(result).isSameAs(entity);
        }
    }

    @Nested
    @StructuralTest
    @DisplayName("Structural")
    class Structural {

        @Test
        @DisplayName("missing name for entity lookup -> not found error")
        void missingNameForEntityLookupShouldReturnNotFoundError() {
            when(roleRepo.findByName("ADMIN")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getRoleInstance.entityByName("ADMIN"))
                    .isInstanceOf(RoleNotFoundException.class)
                    .hasMessage("Could not find role with name ADMIN");

        }
    }
}
