package br.org.gam.api.account.application.useCases.RegisterAccount;

import br.org.gam.api.account.application.AccountConflictException;
import br.org.gam.api.account.application.AccountMapper;
import br.org.gam.api.account.domain.Account;
import br.org.gam.api.account.domain.MyEmail;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.account.persistence.AccountRepository;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Register Account Use Case")
class SpringRegisterAccountTest {

    @Mock
    private AccountRepository accountRepo;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AccountMapper accountMapper;

    @InjectMocks
    private SpringRegisterAccount registerAccount;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - available email -> account is registered")
        void availableEmailShouldRegisterAccount() {
            RegisterAccountDTO dto = new RegisterAccountDTO(MyEmail.of("USER@example.com"), "plain-password", " Eduardo ");
            AccountEntity mappedEntity = new AccountEntity();
            AccountEntity savedEntity = new AccountEntity();
            RegisterAccountRDTO expectedResponse = new RegisterAccountRDTO(UUID.randomUUID());

            when(accountRepo.existsByEmail(dto.email())).thenReturn(false);
            when(passwordEncoder.encode(dto.password())).thenReturn("encoded-password");
            when(accountMapper.domainToEntity(any(Account.class))).thenReturn(mappedEntity);
            when(accountRepo.save(mappedEntity)).thenReturn(savedEntity);
            when(accountMapper.entityToRegisterAccountRDTO(savedEntity)).thenReturn(expectedResponse);

            RegisterAccountRDTO response = registerAccount.register(dto);

            assertThat(response).isSameAs(expectedResponse);

            ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
            verify(accountMapper).domainToEntity(accountCaptor.capture());
            Account registeredAccount = accountCaptor.getValue();

            assertThat(registeredAccount.getId()).isNotNull();
            assertThat(registeredAccount.getId().version()).isEqualTo(7);
            assertThat(registeredAccount.getEmail()).isEqualTo(MyEmail.of("user@example.com"));
            assertThat(registeredAccount.getPasswordHash()).isEqualTo("encoded-password");
            assertThat(registeredAccount.getDisplayName()).isEqualTo("Eduardo");
            verify(accountRepo).save(mappedEntity);
        }

        @Test
        @DisplayName("EP - already registered email -> conflict error")
        void alreadyRegisteredEmailShouldReturnConflictError() {
            RegisterAccountDTO dto = new RegisterAccountDTO(MyEmail.of("user@example.com"), "plain-password", "Eduardo");

            when(accountRepo.existsByEmail(dto.email())).thenReturn(true);

            assertThatThrownBy(() -> registerAccount.register(dto))
                    .isInstanceOf(AccountConflictException.class)
                    .hasMessage("Email 'user@example.com' already registered.");

            verifyNoInteractions(passwordEncoder, accountMapper);
            verify(accountRepo, never()).save(any());
        }
    }
}
