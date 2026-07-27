package br.org.gam.api.shared.maintenance;

import br.org.gam.api.account.application.AccountEntityLoader;
import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.rbac.accountRole.application.useCases.ManageSudoRole;
import br.org.gam.api.shared.activitylog.ActivityReasonNormalizer;
import br.org.gam.api.shared.activitylog.DeveloperActorReference;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("maintenance")
@ConditionalOnProperty(name = "maintenance.job", havingValue = "sudo")
public class ManageSudoMaintenanceJob implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ManageSudoMaintenanceJob.class);

    private final ManageSudoRole manageSudoRole;
    private final AccountEntityLoader accountEntityLoader;
    private final ConfigurableApplicationContext applicationContext;

    public ManageSudoMaintenanceJob(ManageSudoRole manageSudoRole, AccountEntityLoader accountEntityLoader,
                                    ConfigurableApplicationContext applicationContext) {
        this.manageSudoRole = manageSudoRole;
        this.accountEntityLoader = accountEntityLoader;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        String action = requiredOption(args, "maintenance.action");
        validateAction(action);
        AccountSelector selector = requiredAccountSelector(args);
        String reason = requiredReason(args);
        DeveloperActorReference.resolveRequired();
        UUID accountId = selector.resolve(accountEntityLoader);

        switch (action) {
            case "assign-sudo" -> {
                manageSudoRole.assignSudo(accountId, reason);
                log.info("Assigned SUDO role to account {}.", accountId);
            }
            case "remove-sudo" -> {
                manageSudoRole.removeSudo(accountId, reason);
                log.info("Removed SUDO role from account {}.", accountId);
            }
            default -> throw new IllegalStateException("Validated SUDO maintenance action became unsupported.");
        }

        SpringApplication.exit(applicationContext, () -> 0);
    }

    private void validateAction(String action) {
        if (!"assign-sudo".equals(action) && !"remove-sudo".equals(action)) {
            throw new IllegalArgumentException("Unsupported maintenance.action for sudo job: " + action);
        }
    }

    private AccountSelector requiredAccountSelector(ApplicationArguments args) {
        String accountId = rawOption(args, "maintenance.account-id");
        String accountEmail = rawOption(args, "maintenance.account-email");

        if (accountId != null && accountEmail != null) {
            throw new IllegalArgumentException(
                    "Use only one account selector: --maintenance.account-id or --maintenance.account-email."
            );
        }

        if (accountId != null) {
            if (accountId.isBlank()) {
                throw new IllegalArgumentException("Account selector must not be blank.");
            }
            return new AccountSelector(UUID.fromString(accountId.trim()), null);
        }

        if (accountEmail != null) {
            if (accountEmail.isBlank()) {
                throw new IllegalArgumentException("Account selector must not be blank.");
            }
            return new AccountSelector(null, GamEmail.of(accountEmail.trim()));
        }

        throw new IllegalArgumentException(
                "Missing required account selector --maintenance.account-id or --maintenance.account-email."
        );
    }

    private String requiredOption(ApplicationArguments args, String name) {
        String value = optionalOption(args, name);
        if (value == null) {
            throw new IllegalArgumentException("Missing required option --" + name);
        }
        return value;
    }

    private String requiredReason(ApplicationArguments args) {
        String reason = rawOption(args, "maintenance.reason");
        if (reason == null) {
            throw new IllegalArgumentException("Missing required option --maintenance.reason");
        }
        try {
            return ActivityReasonNormalizer.normalizeRequired(reason);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("SUDO role changes require an audit reason.");
        }
    }

    private String rawOption(ApplicationArguments args, String name) {
        if (!args.containsOption(name)) {
            return null;
        }

        List<String> values = args.getOptionValues(name);
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.getFirst();
    }

    private String optionalOption(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.isEmpty() || values.getFirst().isBlank()) {
            return null;
        }
        return values.getFirst().trim();
    }

    private record AccountSelector(UUID accountId, GamEmail accountEmail) {
        UUID resolve(AccountEntityLoader accountEntityLoader) {
            return accountId != null
                    ? accountId
                    : accountEntityLoader.requiredByEmail(accountEmail).getId();
        }
    }
}
