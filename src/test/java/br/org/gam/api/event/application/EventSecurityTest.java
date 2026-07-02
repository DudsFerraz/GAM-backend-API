package br.org.gam.api.event.application;

import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.rbac.Permission.persistence.PermissionEntity;
import br.org.gam.api.security.SecurityUtils;
import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Event Security Helper")
class EventSecurityTest {

    @Mock
    private SecurityUtils securityUtils;

    @Nested
    @StructuralTest
    @DisplayName("Structural")
    class Structural {

        @Test
        @DisplayName("event without required permission -> visible")
        void eventWithoutRequiredPermissionShouldBeVisible() {
            EventSecurity eventSecurity = new EventSecurity(securityUtils);
            EventEntity event = new EventEntity();

            assertThat(eventSecurity.canGetEvent(event)).isTrue();
            verifyNoInteractions(securityUtils);
        }

        @Test
        @DisplayName("event required permission in authorities -> visible")
        void eventRequiredPermissionInAuthoritiesShouldBeVisible() {
            EventSecurity eventSecurity = new EventSecurity(securityUtils);
            EventEntity event = eventWithRequiredPermission("EVENT_GET_S");

            when(securityUtils.getLoggedUserAuthorities()).thenReturn(Set.of("EVENT_GET_S"));

            assertThat(eventSecurity.canGetEvent(event)).isTrue();
        }

        @Test
        @DisplayName("event required permission missing from authorities -> hidden")
        void eventRequiredPermissionMissingFromAuthoritiesShouldBeHidden() {
            EventSecurity eventSecurity = new EventSecurity(securityUtils);
            EventEntity event = eventWithRequiredPermission("EVENT_GET_S");

            when(securityUtils.getLoggedUserAuthorities()).thenReturn(Set.of("EVENT_SEARCH"));

            assertThat(eventSecurity.canGetEvent(event)).isFalse();
        }
    }

    private static EventEntity eventWithRequiredPermission(String permissionName) {
        PermissionEntity permission = new PermissionEntity();
        permission.setName(permissionName);
        EventEntity event = new EventEntity();
        event.setRequiredPermission(permission);
        return event;
    }
}
