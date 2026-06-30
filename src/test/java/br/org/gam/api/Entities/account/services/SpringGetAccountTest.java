package br.org.gam.api.Entities.account.services;

import br.org.gam.api.Entities.RBAC.accountRole.services.getAccountRoles.AccountRolesRDTO;
import br.org.gam.api.Entities.account.AccountMapper;
import br.org.gam.api.Entities.account.exception.AccountNotFoundException;
import br.org.gam.api.Entities.account.myEmail.MyEmail;
import br.org.gam.api.Entities.account.persistence.AccountEntity;
import br.org.gam.api.Entities.account.services.getAccount.SpringGetAccount;
import br.org.gam.api.Entities.account.services.getAccountInstance.GetAccountInstance;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Get Account Use Case")
class SpringGetAccountTest {

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private GetAccountInstance getAccountInstance;

    @InjectMocks
    private SpringGetAccount getAccount;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - existing id -> account response")
        void existingIdShouldReturnAccountResponse() {
            UUID id = UUID.randomUUID();
            AccountEntity entity = new AccountEntity();
            AccountRDTO expectedResponse = new AccountRDTO(
                    id,
                    MyEmail.of("user@example.com"),
                    "Eduardo",
                    new AccountRolesRDTO()
            );

            when(getAccountInstance.entityById(id)).thenReturn(entity);
            when(accountMapper.entityToAccountRDTO(entity)).thenReturn(expectedResponse);

            AccountRDTO response = getAccount.byId(id);

            assertThat(response).isSameAs(expectedResponse);
            verify(getAccountInstance).entityById(id);
            verify(accountMapper).entityToAccountRDTO(entity);
        }

        @Test
        @DisplayName("EP - missing id -> not found error")
        void missingIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(getAccountInstance.entityById(id))
                    .thenThrow(new AccountNotFoundException("Could not find account with id " + id));

            assertThatThrownBy(() -> getAccount.byId(id))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessage("Could not find account with id " + id);

            verify(getAccountInstance).entityById(id);
            verifyNoInteractions(accountMapper);
        }
    }
}
