package br.org.gam.api.event.application;

import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.security.SecurityUtils;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component("eventSecurity")
public class EventSecurity {
    private final SecurityUtils securityUtils;
    public EventSecurity(SecurityUtils securityUtils) {
        this.securityUtils = securityUtils;
    }

    public boolean canGetEvent(EventEntity eventEntity) {
        if(eventEntity.getRequiredPermission() == null) return true;

        Set<String> userAuthorities = securityUtils.getLoggedUserAuthorities();

        return userAuthorities.contains(eventEntity.getRequiredPermission().getName());
    }
}
