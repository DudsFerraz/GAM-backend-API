package br.org.gam.api.rbac.AccountRole.application.useCases.GetAccountRoleInstance;

import br.org.gam.api.account.domain.Account;
import br.org.gam.api.rbac.AccountRole.application.AccountRoleDTO;
import br.org.gam.api.rbac.AccountRole.application.AccountRoleNotFoundException;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleRepository;
import br.org.gam.api.testing.annotation.FunctionalTest;
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
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Get Account Role Instance Use Case")
class SpringGetAccountRoleInstanceTest {

    @Mock
    private AccountRoleRepository accountRoleRepo;

    @InjectMocks
    private SpringGetAccountRoleInstance getAccountRoleInstance;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - existing account and role ids -> account role entity")
        void existingAccountAndRoleIdsShouldReturnAccountRoleEntity() {
            AccountRoleDTO dto = new AccountRoleDTO(UUID.randomUUID(), UUID.randomUUID());
            AccountRoleEntity entity = new AccountRoleEntity();

            when(accountRoleRepo.findByAccount_IdAndRole_Id(dto.accountId(), dto.roleId())).thenReturn(Optional.of(entity));

            AccountRoleEntity result = getAccountRoleInstance.entityByDTO(dto);

            assertThat(result).isSameAs(entity);
        }

        @Test
        @DisplayName("EP - missing account and role ids -> not found error")
        void missingAccountAndRoleIdsShouldReturnNotFoundError() {
            AccountRoleDTO dto = new AccountRoleDTO(UUID.randomUUID(), UUID.randomUUID());

            when(accountRoleRepo.findByAccount_IdAndRole_Id(dto.accountId(), dto.roleId())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getAccountRoleInstance.entityByDTO(dto))
                    .isInstanceOf(AccountRoleNotFoundException.class)
                    .hasMessage("Account with id: " + dto.accountId() + " does not have role with id: " + dto.roleId());
        }
    }
}
