package br.org.gam.api.account.application.useCases.RegisterAccount;

import br.org.gam.api.account.application.AccountMapper;
import br.org.gam.api.account.domain.Account;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.account.persistence.AccountRepository;
import br.org.gam.api.shared.exception.ConflictException;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegisterAccount {

    private final AccountRepository accountRepo;
    private final PasswordEncoder passwordEncoder;
    private final AccountMapper accountMapper;

    public RegisterAccount(AccountRepository accountRepo, PasswordEncoder passwordEncoder, AccountMapper accountMapper) {
        this.accountRepo = accountRepo;
        this.passwordEncoder = passwordEncoder;
        this.accountMapper = accountMapper;
    }

    @Transactional
    public RegisterAccountRDTO register(RegisterAccountDTO dto) {
        if (accountRepo.existsByEmail(dto.email())){
            throw ConflictException.resource("Account", dto.email(), "Email '" + dto.email() + "' already registered.");
        }

        String hashedPassword = passwordEncoder.encode(dto.password());

        Account newAccount = Account.register(dto.email(), hashedPassword, dto.displayName());
        AccountEntity newAccountEntity = accountMapper.domainToEntity(newAccount);
        AccountEntity savedAccountEntity = accountRepo.save(newAccountEntity);

        return accountMapper.entityToRegisterAccountRDTO(savedAccountEntity);
    }
}
