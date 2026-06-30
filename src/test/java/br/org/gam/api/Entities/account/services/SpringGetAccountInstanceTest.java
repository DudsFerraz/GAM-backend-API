package br.org.gam.api.Entities.account.services;

import br.org.gam.api.Entities.account.Account;
import br.org.gam.api.Entities.account.AccountMapper;
import br.org.gam.api.Entities.account.exception.AccountNotFoundException;
import br.org.gam.api.Entities.account.myEmail.MyEmail;
import br.org.gam.api.Entities.account.persistence.AccountEntity;
import br.org.gam.api.Entities.account.persistence.AccountRepository;
import br.org.gam.api.Entities.account.services.getAccountInstance.SpringGetAccountInstance;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Get Account Instance Use Case")
class SpringGetAccountInstanceTest {

    @Mock
    private AccountRepository accountRepo;

    @Mock
    private AccountMapper accountMapper;

    @InjectMocks
    private SpringGetAccountInstance getAccountInstance;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - existing id -> domain account")
        void existingIdShouldReturnDomainAccount() {
            UUID id = UUID.randomUUID();
            AccountEntity entity = new AccountEntity();
            Account domain = Account.register(MyEmail.of("user@example.com"), "encoded-password", "Eduardo");

            when(accountRepo.findById(id)).thenReturn(Optional.of(entity));
            when(accountMapper.entityToDomain(entity)).thenReturn(domain);

            Account result = getAccountInstance.domainById(id);

            assertThat(result).isSameAs(domain);
            verify(accountMapper).entityToDomain(entity);
        }

        @Test
        @DisplayName("EP - existing id -> account entity")
        void existingIdShouldReturnAccountEntity() {
            UUID id = UUID.randomUUID();
            AccountEntity entity = new AccountEntity();

            when(accountRepo.findById(id)).thenReturn(Optional.of(entity));

            AccountEntity result = getAccountInstance.entityById(id);

            assertThat(result).isSameAs(entity);
            verifyNoInteractions(accountMapper);
        }

        @Test
        @DisplayName("EP - missing id -> not found error")
        void missingIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(accountRepo.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getAccountInstance.entityById(id))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessage("Could not find account with id " + id);

            verifyNoInteractions(accountMapper);
        }

        @Test
        @DisplayName("EP - existing email -> domain account")
        void existingEmailShouldReturnDomainAccount() {
            MyEmail email = MyEmail.of("user@example.com");
            AccountEntity entity = new AccountEntity();
            Account domain = Account.register(email, "encoded-password", "Eduardo");

            when(accountRepo.findByEmail(email)).thenReturn(Optional.of(entity));
            when(accountMapper.entityToDomain(entity)).thenReturn(domain);

            Account result = getAccountInstance.domainByEmail(email);

            assertThat(result).isSameAs(domain);
            verify(accountMapper).entityToDomain(entity);
        }

        @Test
        @DisplayName("EP - existing email -> account entity")
        void existingEmailShouldReturnAccountEntity() {
            MyEmail email = MyEmail.of("user@example.com");
            AccountEntity entity = new AccountEntity();

            when(accountRepo.findByEmail(email)).thenReturn(Optional.of(entity));

            AccountEntity result = getAccountInstance.entityByEmail(email);

            assertThat(result).isSameAs(entity);
            verifyNoInteractions(accountMapper);
        }

        @Test
        @DisplayName("EP - missing email -> not found error")
        void missingEmailShouldReturnNotFoundError() {
            MyEmail email = MyEmail.of("missing@example.com");

            when(accountRepo.findByEmail(email)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getAccountInstance.entityByEmail(email))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessage("Could not find account with email missing@example.com");

            verifyNoInteractions(accountMapper);
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

            when(accountRepo.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getAccountInstance.domainById(id))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessage("Could not find account with id " + id);

            verifyNoInteractions(accountMapper);
        }

        @Test
        @DisplayName("missing email for domain lookup -> not found error")
        void missingEmailForDomainLookupShouldReturnNotFoundError() {
            MyEmail email = MyEmail.of("missing@example.com");

            when(accountRepo.findByEmail(email)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getAccountInstance.domainByEmail(email))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessage("Could not find account with email missing@example.com");

            verifyNoInteractions(accountMapper);
        }
    }
}
