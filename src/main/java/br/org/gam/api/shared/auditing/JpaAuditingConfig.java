package br.org.gam.api.shared.auditing;

import br.org.gam.api.security.application.AccountDetails;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
public class JpaAuditingConfig implements AuditorAware<UUID> {

    @Override
    public Optional<UUID> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null ||
                !authentication.isAuthenticated() ||
                authentication.getPrincipal().equals("anonymousUser")) {

            return Optional.empty();
        }

        AccountDetails userDetails = (AccountDetails) authentication.getPrincipal();

        return Optional.of(userDetails.getId());
    }
}