package br.org.gam.api.rbac.accountRole.application.useCases;

import br.org.gam.api.rbac.accountRole.application.AccountRolesRDTO;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleRepository;
import br.org.gam.api.rbac.role.application.RoleMapper;
import br.org.gam.api.rbac.role.application.RoleRDTO;
import br.org.gam.api.rbac.role.persistence.RoleEntity;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Get Account Roles Use Case")
class GetAccountRolesTest {

    @Mock
    private AccountRoleRepository accountRoleRepo;

    @Mock
    private RoleMapper roleMapper;

    @InjectMocks
    private GetAccountRoles getAccountRoles;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - account with roles -> mapped roles response")
        void accountWithRolesShouldReturnMappedRolesResponse() {
            UUID accountId = UUID.randomUUID();
            RoleEntity firstRole = roleEntity();
            RoleEntity secondRole = roleEntity();
            AccountRoleEntity firstAccountRole = accountRoleEntity(firstRole);
            AccountRoleEntity secondAccountRole = accountRoleEntity(secondRole);
            RoleRDTO firstResponse = response(UUID.randomUUID(), "ADMIN");
            RoleRDTO secondResponse = response(UUID.randomUUID(), "MEMBER");

            when(accountRoleRepo.findAllByAccount_Id(accountId)).thenReturn(List.of(firstAccountRole, secondAccountRole));
            when(roleMapper.entityToRDTO(firstRole)).thenReturn(firstResponse);
            when(roleMapper.entityToRDTO(secondRole)).thenReturn(secondResponse);

            AccountRolesRDTO response = getAccountRoles.get(accountId);

            assertThat(response.roles()).containsExactlyInAnyOrder(firstResponse, secondResponse);
            verify(roleMapper).entityToRDTO(firstRole);
            verify(roleMapper).entityToRDTO(secondRole);
        }
    }

    @Nested
    @StructuralTest
    @DisplayName("Structural")
    class Structural {

        @Test
        @DisplayName("account without roles -> empty roles response")
        void accountWithoutRolesShouldReturnEmptyRolesResponse() {
            UUID accountId = UUID.randomUUID();

            when(accountRoleRepo.findAllByAccount_Id(accountId)).thenReturn(List.of());

            AccountRolesRDTO response = getAccountRoles.get(accountId);

            assertThat(response.roles()).isEmpty();
            verifyNoInteractions(roleMapper);
        }
    }

    private static AccountRoleEntity accountRoleEntity(RoleEntity role) {
        AccountRoleEntity accountRole = new AccountRoleEntity();
        accountRole.setRole(role);
        return accountRole;
    }

    private static RoleEntity roleEntity() {
        RoleEntity role = new RoleEntity();
        role.setId(UUID.randomUUID());
        role.setName("ADMIN");
        role.setDescription("System administrator");
        return role;
    }

    private static RoleRDTO response(UUID id, String name) {
        return new RoleRDTO(id, name, "Description");
    }
}
