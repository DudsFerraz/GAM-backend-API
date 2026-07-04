package br.org.gam.api.account.application.useCases;

import br.org.gam.api.account.application.AccountMapper;
import br.org.gam.api.account.application.AccountRDTO;
import br.org.gam.api.account.application.AccountEntityLoader;
import br.org.gam.api.account.domain.Account;
import br.org.gam.api.account.domain.MyEmail;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.rbac.AccountRole.application.AccountRolesRDTO;
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
@DisplayName("Get Account Use Case")
class GetAccountTest {

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private AccountEntityLoader getAccountInstance;

    @InjectMocks
    private GetAccount getAccount;

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

            when(getAccountInstance.requiredById(id)).thenReturn(entity);
            when(accountMapper.entityToRDTO(entity)).thenReturn(expectedResponse);

            AccountRDTO response = getAccount.byId(id);

            assertThat(response).isSameAs(expectedResponse);
            verify(getAccountInstance).requiredById(id);
            verify(accountMapper).entityToRDTO(entity);
        }

        @Test
        @DisplayName("EP - missing id -> not found error")
        void missingIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(getAccountInstance.requiredById(id))
                    .thenThrow(NotFoundException.resource("Account", id));

            assertThatThrownBy(() -> getAccount.byId(id))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Account not found with identifier " + id);

            verify(getAccountInstance).requiredById(id);
            verifyNoInteractions(accountMapper);
        }
    }
}
