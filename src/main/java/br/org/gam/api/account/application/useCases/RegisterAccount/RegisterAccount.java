package br.org.gam.api.account.application.useCases.registerAccount;

import br.org.gam.api.account.application.AccountMapper;
import br.org.gam.api.account.domain.Account;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.account.persistence.AccountRepository;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.exception.ConflictException;
import jakarta.transaction.Transactional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegisterAccount {

    private static final String ACCOUNT_EMAIL_UNIQUE_INDEX = "idx_accounts_email_not_deleted";

    private final AccountRepository accountRepo;
    private final PasswordEncoder passwordEncoder;
    private final AccountMapper accountMapper;
    private final ActivityEvents activityEvents;

    public RegisterAccount(
            AccountRepository accountRepo,
            PasswordEncoder passwordEncoder,
            AccountMapper accountMapper,
            ActivityEvents activityEvents
    ) {
        this.accountRepo = accountRepo;
        this.passwordEncoder = passwordEncoder;
        this.accountMapper = accountMapper;
        this.activityEvents = activityEvents;
    }

    @Transactional
    public RegisterAccountRDTO register(RegisterAccountDTO dto) {
        if (accountRepo.existsByEmail(dto.email())){
            throw ConflictException.resource("Account", dto.email(), "Email '" + dto.email() + "' already registered.");
        }

        String displayName = dto.displayName().trim();
        if (displayName.isEmpty() || displayName.length() > 50) {
            throw new IllegalArgumentException("Display name must be between 1 and 50 characters after trimming.");
        }

        String hashedPassword = passwordEncoder.encode(dto.password());

        Account newAccount = Account.register(dto.email(), hashedPassword, displayName);
        AccountEntity newAccountEntity = accountMapper.domainToEntity(newAccount);
        AccountEntity savedAccountEntity;
        try {
            savedAccountEntity = accountRepo.save(newAccountEntity);
            accountRepo.flush();
        } catch (DataIntegrityViolationException exception) {
            if (!isEmailUniqueConstraintViolation(exception)) {
                throw exception;
            }
            throw ConflictException.resource(
                    "Account",
                    dto.email(),
                    "Email '" + dto.email() + "' already registered."
            );
        }

        RegisterAccountRDTO response = accountMapper.entityToRegisterAccountRDTO(savedAccountEntity);
        activityEvents.accountRegistered(response.id());
        return response;
    }

    private boolean isEmailUniqueConstraintViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                return ACCOUNT_EMAIL_UNIQUE_INDEX.equals(constraintViolation.getConstraintName());
            }
            cause = cause.getCause();
        }
        return false;
    }
}
